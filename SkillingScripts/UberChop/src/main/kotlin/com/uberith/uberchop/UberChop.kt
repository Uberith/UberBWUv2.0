package com.uberith.uberchop

import com.uberith.api.SuspendableScript
import com.uberith.uberchop.gui.UberChopGUI
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.inventories.InventoryManager
import net.botwithus.rs3.world.World
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import org.slf4j.LoggerFactory
import java.util.Locale

@Info(
    name = "UberChop",
    description = "Smart RS3 woodcutting: targets trees, chops, optional banking.",
    version = "0.2.0",
    author = "Uberith"
)
class UberChop : SuspendableScript() {
    // Centralized settings used by GUI and runtime
    val settings = Settings()
    var targetTree: String = "Tree"
    // Currently selected chopping location (display + logic)
    var location: String = ""
    var bankWhenFull: Boolean = false
    var logsChopped: Int = 0
    var status: String = "Idle"
    private var totalRuntimeMs: Long = 0L
    private var lastRuntimeUpdateMs: Long = 0L

    private var currentTarget: SceneObject? = null
    private var lastTargetTs: Long = 0L
    private var interactFailCount: Int = 0
    private val retargetCooldownMs: Long = 1500L
    private val maxInteractFailsBeforeRetarget = 2

    enum class Phase { READY, PREPARING, CHOPPING, BANKING }
    @Volatile var phase: Phase = Phase.READY

    private val gui = UberChopGUI(this)
    private val log = LoggerFactory.getLogger(UberChop::class.java)
    private fun dbg(msg: String) { try { com.uberith.api.utils.CustomLogger.debug(msg, "UberChop") } catch (_: Throwable) {}; try { log.debug(msg) } catch (_: Throwable) {} }
    private fun inf(msg: String) { try { com.uberith.api.utils.CustomLogger.info(msg, "UberChop") } catch (_: Throwable) {}; try { log.info(msg) } catch (_: Throwable) {} }
    private fun warn(msg: String) { try { com.uberith.api.utils.CustomLogger.warning(msg, "UberChop") } catch (_: Throwable) {}; try { log.warn(msg) } catch (_: Throwable) {} }

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        tryBindLoggerConsole(workspace)
        gui.render(workspace)
    }

    private var loggerBound: Boolean = false
    private fun tryBindLoggerConsole(workspace: Workspace) {
        if (loggerBound) return
        try {
            val m = workspace.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("console") }
            val console = m?.invoke(workspace)
            com.uberith.api.utils.CustomLogger.initialize(console, "UberChop")
            loggerBound = true
            inf("Logger bound to ScriptConsole")
        } catch (_: Throwable) { }
    }

    override fun onActivation() {
        super.onActivation()
        // Initialize targetTree from saved settings
        try {
            val idx = settings.savedTreeType.coerceIn(0, TreeTypes.ALL.size - 1)
            targetTree = TreeTypes.ALL[idx]
        } catch (_: Throwable) { /* keep default */ }
        // Initialize location from settings, or default to first matching location
        try {
            val saved = settings.savedLocation
            location = if (saved.isNotBlank() && treeLocations.any { it.name == saved }) {
                saved
            } else {
                // prefer first location that supports current target tree; fallback to first overall
                treeLocations.firstOrNull { loc ->
                    loc.availableTrees.any { at ->
                        val a = at.lowercase(java.util.Locale.ROOT)
                        val b = targetTree.lowercase(java.util.Locale.ROOT)
                        a.contains(b) || b.contains(a)
                    }
                }?.name ?: treeLocations.firstOrNull()?.name ?: ""
            }
            settings.savedLocation = location
        } catch (_: Throwable) { /* ignore */ }
        // Initialize logging to file/console bridge
        try { com.uberith.api.utils.CustomLogger.initialize(null, "UberChop") } catch (_: Throwable) {}
        log.info("UberChop activated")
        status = "Active - Preparing"
        phase = Phase.PREPARING
        // Preload GUI assets (e.g., logo texture)
        try { gui.preload() } catch (_: Throwable) {}
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        super.onDeactivation()
        log.info("UberChop deactivated")
        status = "Inactive"
        phase = Phase.READY
    }

    override suspend fun onLoop() {
        // Accumulate runtime only while loop is actively running
        try {
            val now = System.currentTimeMillis()
            if (lastRuntimeUpdateMs != 0L) totalRuntimeMs += (now - lastRuntimeUpdateMs)
            lastRuntimeUpdateMs = now
        } catch (_: Throwable) { }
        when (phase) {
            Phase.READY -> {
                status = "Starting..."; phase = Phase.PREPARING; awaitTicks(1)
            }
            Phase.PREPARING -> {
                // If we have a target location with a chop tile, head there first when far away
                tryNavigateTowardSelectedChop()
                val t = pickRandomInteractableTree(targetTree)
                if (t == null) {
                    status = "Preparing: looking for $targetTree nearby"; awaitTicks(2)
                } else {
                    status = "Preparing: ${t.name} found"; currentTarget = t; lastTargetTs = System.currentTimeMillis(); interactFailCount = 0
                    awaitTicks(1); phase = Phase.CHOPPING
                }
            }
            Phase.BANKING -> {
                status = "Banking: opening"
                var opened = bankOpenApiOrXapi()
                awaitTicks(3)
                if (!opened) {
                    // Navigate to bank tile and retry
                    opened = bankTravelAndRetryOpen()
                    awaitTicks(3)
                    if (!opened) { status = "Banking: open failed"; awaitTicks(2); phase = Phase.CHOPPING; return }
                }

                // Empty wood box if option present
                tryEmptyWoodBoxXapi()

                // Deposit only logs + nests via API/XAPI
                bankDepositLogsAndNestsAny()
                awaitTicks(2)
                bankCloseApiOrXapi()
                awaitTicks(2)
                phase = Phase.CHOPPING
            }
            Phase.CHOPPING -> {
                if (isBackpackFull()) {
                    inf("Backpack full: attempting Wood Box 'Fill' before banking")
                    val filled = tryFillWoodBox()
                    if (filled) dbg("Wood Box 'Fill' interaction sent")
                    if (!isBackpackFull()) { dbg("Inventory space detected after Wood Box fill; resume chopping"); awaitTicks(2); return }
                    when (settings.logHandlingMode) {
                        1 -> { // Magic Notepaper mode
                            status = "Notepaper: attempting convert"
                            val ok = tryUseMagicNotepaperOnLogs()
                            awaitTicks(3)
                            if (!ok) { status = "Notepaper failed; banking"; phase = Phase.BANKING }
                            return
                        }
                        else -> {
                            if (bankWhenFull || settings.logHandlingMode == 0) {
                                phase = Phase.BANKING; return
                            }
                        }
                    }
                }

                var tree = currentTarget
                if (!isTreeValid(tree, targetTree)) tree = null
                val now = System.currentTimeMillis()
                val allowRetarget = (now - lastTargetTs) >= retargetCooldownMs
                if (tree == null && allowRetarget) {
                    tree = pickRandomInteractableTree(targetTree)
                    if (tree != null) { currentTarget = tree; lastTargetTs = now; interactFailCount = 0; dbg("Target acquired: ${tree.name}") }
                }
                if (tree == null) { status = "No $targetTree nearby"; awaitTicks(2); phase = Phase.PREPARING; return }

                status = "Chopping ${tree.name}"
                val options = tree.getOptions()
                val idx = options.indexOfFirst { it != null && it.lowercase(Locale.ROOT).contains("chop") }
                val interacted = if (idx >= 0) tree.interact(idx) else false
                if (interacted == false) {
                    interactFailCount++
                    status = "Interact failed ($interactFailCount)"
                    if (interactFailCount >= maxInteractFailsBeforeRetarget) { currentTarget = null; lastTargetTs = 0L }
                    awaitTicks(2); return
                }
                awaitTicks(3)
                logsChopped++
                status = "Chopped ($logsChopped)"
                awaitTicks(1)
            }
        }
    }

    private fun tryNavigateTowardSelectedChop() {
        try {
            val sel = treeLocations.firstOrNull { it.name == location }
            val dest = effectiveChopFor(sel)
            if (dest != null) {
                val me = LocalPlayer.self() ?: return
                val dist = try { me.distanceTo(dest) } catch (_: Throwable) { 0.0 }
                if (dist > 8.0 && shouldAttemptTravel()) {
                    status = "Travel: ${sel?.name ?: location}"
                    tryWalkTo(dest)
                }
            }
        } catch (_: Throwable) { }
    }

    private var lastTravelAttemptMs: Long = 0L
    private var travelAttempts: Int = 0
    private fun shouldAttemptTravel(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTravelAttemptMs >= 3000L) { // at most every 3s
            lastTravelAttemptMs = now
            travelAttempts = (travelAttempts + 1).coerceAtMost(1000)
            return true
        }
        return false
    }

    private fun effectiveChopFor(loc: TreeLocation?): net.botwithus.rs3.world.Coordinate? {
        if (loc == null) return null
        val o = settings.customLocations[loc.name]
        if (o?.chopX != null && o.chopY != null && o.chopZ != null) {
            return net.botwithus.rs3.world.Coordinate(o.chopX!!, o.chopY!!, o.chopZ!!)
        }
        return loc.chop
    }

    private fun effectiveBankFor(loc: TreeLocation?): net.botwithus.rs3.world.Coordinate? {
        if (loc == null) return null
        val o = settings.customLocations[loc.name]
        if (o?.bankX != null && o.bankY != null && o.bankZ != null) {
            return net.botwithus.rs3.world.Coordinate(o.bankX!!, o.bankY!!, o.bankZ!!)
        }
        return loc.bank
    }

    private fun tryWalkTo(target: net.botwithus.rs3.world.Coordinate) {
        // Try multiple navigation providers reflectively; fallback to local Navigation stub
        val candidates = arrayOf(
            // Potential navigation APIs
            Pair("net.botwithus.navigation.api.Navigator", arrayOf("walkTo", "moveTo", "pathTo")),
            Pair("net.botwithus.navigation.api.Navigation", arrayOf("walkTo", "moveTo", "pathTo")),
            Pair("net.botwithus.api.game.navigation.Navigator", arrayOf("walkTo", "moveTo", "pathTo")),
            Pair("net.botwithus.xapi.game.world.Navigation", arrayOf("walkTo", "moveTo", "pathTo"))
        )
        for ((cn, names) in candidates) {
            try {
                val cls = Class.forName(cn)
                // Try static methods first
                for (mn in names) {
                    try {
                        val m = cls.getMethod(mn, target::class.java)
                        val res = m.invoke(null, target)
                        if (res == null || (res is Boolean && res) || (res is Number && res.toInt() != 0)) return
                    } catch (_: Throwable) { }
                }
                // Try getInstance() then instance methods
                try {
                    val gi = cls.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("instance") }
                    val inst = gi?.invoke(null)
                    if (inst != null) {
                        for (mn in names) {
                            try {
                                val m = inst.javaClass.getMethod(mn, target::class.java)
                                val res = m.invoke(inst, target)
                                if (res == null || (res is Boolean && res) || (res is Number && res.toInt() != 0)) return
                            } catch (_: Throwable) { }
                        }
                    }
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
        // Fallback to script-api stub
        try { com.uberith.api.game.world.Navigation().moveTo(target) } catch (_: Throwable) { }
    }

    private fun tryEmptyWoodBoxXapi() {
        try {
            val cq = Class.forName("net.botwithus.xapi.query.ComponentQuery")
            val newQuery = cq.getMethod("newQuery", Int::class.javaPrimitiveType)
            val q = newQuery.invoke(null, 517) // bank interface index
            val opt = q.javaClass.getMethod("option", String::class.java)
            val q2 = opt.invoke(q, "Empty - logs and bird's nests")
            val results = q2.javaClass.getMethod("results").invoke(q2)
            val firstOrNull = results.javaClass.getMethod("firstOrNull").invoke(results)
            if (firstOrNull != null) {
                val interact = firstOrNull.javaClass.getMethod("interact", Int::class.javaPrimitiveType)
                interact.invoke(firstOrNull, 1)
            }
        } catch (_: Throwable) { }
    }

    private fun xapiBankDepositOnlyLogsAndNests() {
        // Preferred: Bank.depositAll(PermissiveScript, Pattern...)
        // Fallbacks: Bank.deposit(Pattern), else final fallback depositAll()
        try {
            val bank = Class.forName("net.botwithus.xapi.game.inventory.Bank")
            val patternClass = Class.forName("java.util.regex.Pattern")
            val compile = patternClass.getMethod("compile", String::class.java)
            val pLogs = compile.invoke(null, "(?i).*log.*")
            val pNests = compile.invoke(null, "(?i).*nest.*")
            val patterns = arrayOf(pLogs, pNests)

            val depositAllCandidates = bank.methods.filter { it.name == "depositAll" }
            for (m in depositAllCandidates) {
                val p = m.parameterTypes
                if (p.isNotEmpty() && p.last().isArray && p.last().componentType.name == patternClass.name) {
                    val args: Array<Any?>
                    if (p.size == 1) {
                        val varArgArray = java.lang.reflect.Array.newInstance(patternClass, patterns.size)
                        for (i in patterns.indices) java.lang.reflect.Array.set(varArgArray, i, patterns[i])
                        args = arrayOf(varArgArray)
                    } else {
                        val firstParam = p[0]
                        val firstArg: Any? = if (firstParam.isInstance(this)) {
                            this
                        } else if (firstParam.isInterface) {
                            java.lang.reflect.Proxy.newProxyInstance(
                                firstParam.classLoader,
                                arrayOf(firstParam)
                            ) { _, _, _ -> null }
                        } else null
                        val varArgArray = java.lang.reflect.Array.newInstance(patternClass, patterns.size)
                        for (i in patterns.indices) java.lang.reflect.Array.set(varArgArray, i, patterns[i])
                        args = arrayOf(firstArg, varArgArray)
                    }
                    m.isAccessible = true
                    m.invoke(null, *args)
                    return
                }
            }

            // Fallback to deposit(Pattern) called twice if available
            val depositMethods = bank.methods.filter { it.name == "deposit" && it.parameterTypes.size == 1 && it.parameterTypes[0].name == patternClass.name }
            if (depositMethods.isNotEmpty()) {
                val dm = depositMethods.first()
                for (pat in patterns) dm.invoke(null, pat)
                return
            }

            // Last resort: depositAll (least preferred)
            try {
                val m = bank.getMethod("depositAll")
                m.invoke(null)
            } catch (_: Throwable) { }
        } catch (_: Throwable) {
            try {
                val bank = Class.forName("net.botwithus.xapi.game.inventory.Bank")
                val m = bank.getMethod("depositAll")
                m.invoke(null)
            } catch (_: Throwable) { }
        }
    }

    private fun tryUseMagicNotepaperOnLogs(): Boolean {
        // Heuristic reflective attempts across common XAPI inventory helpers
        return try {
            val patternClass = Class.forName("java.util.regex.Pattern")
            val compile = patternClass.getMethod("compile", String::class.java)
            val pNotepaper = compile.invoke(null, "(?i).*magic\\s*notepaper.*")
            val pLogs = compile.invoke(null, "(?i).*log.*")

            val candidates = arrayOf(
                "net.botwithus.xapi.game.inventory.Inventory",
                "net.botwithus.xapi.game.inventory.Backpack",
                "net.botwithus.xapi.game.item.Inventory",
                "net.botwithus.xapi.game.item.Backpack"
            )
            for (cn in candidates) {
                try {
                    val cls = Class.forName(cn)
                    try {
                        val m = cls.getMethod("useItemOnItem", patternClass, patternClass)
                        val res = m.invoke(null, pNotepaper, pLogs)
                        return (res as? Boolean) == true
                    } catch (_: Throwable) { }
                    try {
                        val m = cls.getMethod("useOn", patternClass, patternClass)
                        val res = m.invoke(null, pNotepaper, pLogs)
                        return (res as? Boolean) == true
                    } catch (_: Throwable) { }
                    try {
                        val mUse = cls.getMethod("use", patternClass)
                        val mOn = cls.getMethod("useOn", patternClass)
                        mUse.invoke(null, pNotepaper)
                        val res = mOn.invoke(null, pLogs)
                        return (res as? Boolean) == true
                    } catch (_: Throwable) { }
                } catch (_: Throwable) { }
            }
            false
        } catch (_: Throwable) { false }
    }
    private fun xapiBankClose() {
        try {
            val bank = Class.forName("net.botwithus.xapi.game.inventory.Bank")
            val m = bank.getMethod("close")
            m.invoke(null)
        } catch (_: Throwable) { }
    }

    private fun openNearestBankRs3(center: net.botwithus.rs3.world.Coordinate? = null, maxRadius: Double = 20.0): Boolean {
        val player = LocalPlayer.self() ?: return false
        val bankNameKeys = listOf("bank", "bank booth", "bank chest", "deposit box", "deposit chest")
        val bankOptKeys = listOf("bank", "open", "deposit")
        val objs = World.getSceneObjects().asSequence()
            .filter { it.isHidden() == false }
            .filter {
                val nm = it.name.lowercase(Locale.ROOT)
                val nmMatch = bankNameKeys.any { k -> nm.contains(k) }
                val optMatch = it.getOptions().any { o -> o != null && bankOptKeys.any { k -> o!!.lowercase(Locale.ROOT).contains(k) } }
                nmMatch || optMatch
            }
            .filter { so ->
                if (center == null) true else try {
                    val dx = (so.coordinate.x - center.x).toDouble(); val dy = (so.coordinate.y - center.y).toDouble()
                    Math.hypot(dx, dy) <= maxRadius
                } catch (_: Throwable) { true }
            }
            .sortedBy { so ->
                if (center != null) {
                    val dx = (so.coordinate.x - center.x).toDouble(); val dy = (so.coordinate.y - center.y).toDouble(); Math.hypot(dx, dy)
                } else player.distanceTo(so.coordinate)
            }
            .toList()
        val so = objs.firstOrNull() ?: return false
        val opts = so.getOptions().map { it?.lowercase(Locale.ROOT) ?: "" }
        val pref = listOf("bank", "open", "deposit")
        val idx = pref.asSequence().map { key -> opts.indexOfFirst { it.contains(key) } }.firstOrNull { it >= 0 } ?: -1
        val ok = if (idx >= 0) so.interact(idx) else false
        return ok == true
    }

    private fun pickRandomInteractableTree(name: String, nearestPool: Int = 5): SceneObject? {
        val player = LocalPlayer.self() ?: return null
        val nLower = name.lowercase(Locale.ROOT)
        val candidates = World.getSceneObjects()
            .asSequence()
            .filter { so ->
                try {
                    val visible = (so.isHidden() == false)
                    val matchesName = so.name.lowercase(Locale.ROOT).contains(nLower)
                    val hasChop = so.getOptions().any { it != null && it.lowercase(Locale.ROOT).contains("chop") }
                    visible && matchesName && hasChop
                } catch (_: Throwable) { false }
            }
            .sortedBy { so -> player.distanceTo(so.coordinate) }
            .take(nearestPool.coerceAtLeast(1))
            .toList()
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]
        // Weighted selection biased towards the closest (rank-based weights: N, N-1, ..., 1)
        val n = candidates.size
        val weights = IntArray(n) { i -> (n - i) }
        val total = weights.sum()
        val r = kotlin.random.Random.nextInt(total)
        var acc = 0
        for (i in 0 until n) {
            acc += weights[i]
            if (r < acc) return candidates[i]
        }
        return candidates.first()
    }

    private fun isTreeValid(tree: SceneObject?, expectedName: String): Boolean {
        if (tree == null) return false
        return try {
            val visible = (tree.isHidden() == false)
            val matches = tree.name.lowercase(Locale.ROOT).contains(expectedName.lowercase(Locale.ROOT))
            val hasChop = tree.getOptions().any { it != null && it.lowercase(Locale.ROOT).contains("chop") }
            visible && matches && hasChop
        } catch (_: Throwable) { false }
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
    private fun isBackpackFull(): Boolean {
        // Prefer API/XAPI Backpack.isFull(); fallback to generic inventory scan
        try {
            val cls = Class.forName("net.botwithus.api.game.hud.inventories.Backpack")
            val m = cls.getMethod("isFull")
            val res = m.invoke(null)
            if (res is Boolean) return res
        } catch (_: Throwable) { }
        try {
            val cls = Class.forName("net.botwithus.xapi.game.inventory.Backpack")
            val m = cls.getMethod("isFull")
            val res = m.invoke(null)
            if (res is Boolean) return res
        } catch (_: Throwable) { }
        return try {
            InventoryManager.getInventories().any { inv ->
                try { inv.isFull() } catch (_: Throwable) { false }
            }
        } catch (_: Throwable) { false }
    }

    // Banking helpers (API/XAPI reflection first, with RS3 scene fallback)
    private fun bankOpenApiOrXapi(): Boolean {
        // Try API first
        try {
            val cls = Class.forName("net.botwithus.api.game.hud.inventories.Bank")
            val m = cls.getMethod("open")
            inf("Bank: Attempting API open")
            val res = m.invoke(null)
            if (res is Boolean && res) return waitForBankOpen()
            if (waitForBankOpen()) return true
        } catch (_: Throwable) { }
        // Then XAPI
        try {
            val cls = Class.forName("net.botwithus.xapi.game.inventory.Bank")
            val m = cls.getMethod("open")
            inf("Bank: Attempting XAPI open")
            val res = m.invoke(null)
            if (res is Boolean && res) return waitForBankOpen()
            if (waitForBankOpen()) return true
        } catch (_: Throwable) { }
        // Fallback to direct RS3 scene interaction; prefer near configured bank tile
        try {
            val sel = treeLocations.firstOrNull { it.name == location }
            val pref = effectiveBankFor(sel)
            if (pref != null) {
                inf("Bank: Attempting scene open near preferred bank tile")
                if (openNearestBankRs3(pref, 18.0)) return waitForBankOpen()
            }
        } catch (_: Throwable) { }
        inf("Bank: Attempting scene open (general)")
        if (openNearestBankRs3(null, 25.0)) return waitForBankOpen()
        return false
    }

    private fun bankCloseApiOrXapi() {
        try {
            val cls = Class.forName("net.botwithus.api.game.hud.inventories.Bank")
            val m = cls.getMethod("close")
            m.invoke(null)
            return
        } catch (_: Throwable) { }
        try {
            val cls = Class.forName("net.botwithus.xapi.game.inventory.Bank")
            val m = cls.getMethod("close")
            m.invoke(null)
        } catch (_: Throwable) { }
    }

    private fun bankDepositLogsAndNestsAny() {
        // Attempt API and XAPI deposit by regex pattern; fallback to depositAll
        try {
            val patternClass = Class.forName("java.util.regex.Pattern")
            val compile = patternClass.getMethod("compile", String::class.java)
            val names = listOf(
                // Logs by tier
                "Logs", "Oak logs", "Willow logs", "Maple logs", "Yew logs", "Magic logs", "Elder logs",
                "Teak logs", "Mahogany logs", "Acadia logs", "Arctic pine logs",
                // Non-standard drops
                "Bamboo", "Crystal tree blossom",
                // Misc
                "Bird's nest", "Bird nest"
            )
            val patterns = names.map { n -> compile.invoke(null, "(?i).*" + java.util.regex.Pattern.quote(n) + ".*") }.toTypedArray()

            // API path
            try {
                val bank = Class.forName("net.botwithus.api.game.hud.inventories.Bank")
                // Try deposit(String)
                val depStr = bank.methods.firstOrNull { it.name == "deposit" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
                if (depStr != null) {
                    for (n in names) depStr.invoke(null, n)
                    return
                }
                val deposit = bank.methods.firstOrNull { it.name == "deposit" && it.parameterCount == 1 && it.parameterTypes[0].name == patternClass.name }
                if (deposit != null) {
                    for (pat in patterns) deposit.invoke(null, pat)
                    return
                }
                // depositAll as last resort
                try { bank.getMethod("depositAll").invoke(null) } catch (_: Throwable) { }
                return
            } catch (_: Throwable) { }

            // XAPI path
            try {
                val bank = Class.forName("net.botwithus.xapi.game.inventory.Bank")
                // Try deposit(String)
                val depStr = bank.methods.firstOrNull { it.name == "deposit" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
                if (depStr != null) {
                    for (n in names) depStr.invoke(null, n)
                    return
                }
                val deposit = bank.methods.firstOrNull { it.name == "deposit" && it.parameterCount == 1 && it.parameterTypes[0].name == patternClass.name }
                if (deposit != null) {
                    for (pat in patterns) deposit.invoke(null, pat)
                    return
                }
                // deposit(Pattern...) vararg
                val methods = bank.methods.filter { it.name == "deposit" }
                val vararg = methods.firstOrNull { m ->
                    val p = m.parameterTypes
                    p.isNotEmpty() && p.last().isArray && p.last().componentType.name == patternClass.name
                }
                if (vararg != null) {
                    val varArgArray = java.lang.reflect.Array.newInstance(patternClass, patterns.size)
                    for (i in patterns.indices) java.lang.reflect.Array.set(varArgArray, i, patterns[i])
                    val p = vararg.parameterTypes
                    if (p.size == 1) vararg.invoke(null, varArgArray) else vararg.invoke(null, null, varArgArray)
                    return
                }
                try { bank.getMethod("depositAll").invoke(null) } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        } catch (_: Throwable) {
            // swallow
        }
    }

    private fun bankTravelAndRetryOpen(): Boolean {
        return try {
            val sel = treeLocations.firstOrNull { it.name == location }
            val dest = effectiveBankFor(sel)
            if (dest != null) {
                val me = LocalPlayer.self()
                val far = try { me?.distanceTo(dest) ?: 999.0 } catch (_: Throwable) { 999.0 }
                if (far > 6.0) {
                    status = "Travel: Bank"
                    tryWalkTo(dest)
                }
            }
            bankOpenApiOrXapi()
        } catch (_: Throwable) { false }
    }

    private fun isBankOpen(): Boolean {
        try {
            val api = Class.forName("net.botwithus.api.game.hud.inventories.Bank")
            val m = api.getMethod("isOpen")
            val res = m.invoke(null)
            if (res is Boolean) return res
        } catch (_: Throwable) { }
        try {
            val xapi = Class.forName("net.botwithus.xapi.game.inventory.Bank")
            val m = xapi.getMethod("isOpen")
            val res = m.invoke(null)
            if (res is Boolean) return res
        } catch (_: Throwable) { }
        return false
    }

    private fun waitForBankOpen(timeoutMs: Long = 2500L, pollMs: Long = 100L): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (isBankOpen()) return true
            try { Thread.sleep(pollMs) } catch (_: Throwable) { }
        }
        return false
    }

    private fun woodBoxNames(): List<String> {
        val base = mutableSetOf<String>()
        base += "wood box"
        try {
            for (t in TreeTypes.ALL) {
                var n = t.lowercase(java.util.Locale.ROOT)
                n = n.replace(" tree", "").trim()
                base += "$n wood box"
                if (n == "acadia") base += "acacia wood box"
                if (n == "arctic") base += "arctic pine wood box"
                if (n == "magic") base += "eternal magic wood box"
            }
        } catch (_: Throwable) {}
        base += listOf(
            "arctic wood box", "crystal wood box", "bamboo wood box", "eucalyptus wood box",
            "ivy wood box"
        )
        return base.toList()
    }

    private fun tryFillWoodBox(): Boolean {
        // Attempts to find any wood box and interact with "Fill"
        val patterns = woodBoxNames()
        val actions = listOf(
            "Fill",
            "Fill wood box",
            "Fill box",
            "Fill with logs",
            "Fill logs"
        )
        // API: scan items and attempt best-match actions
        try {
            val cls = Class.forName("net.botwithus.api.game.hud.inventories.Backpack")
            // Fast path: contains(name) then interact(name, "Fill")
            try {
                val contains = cls.getMethod("contains", String::class.java)
                val interact = cls.getMethod("interact", String::class.java, String::class.java)
                for (p in patterns) {
                    try {
                        val has = contains.invoke(null, p) as? Boolean
                        if (has == true) {
                            for (act in actions) {
                                val ok = interact.invoke(null, p, act)
                                if (ok is Boolean && ok) { inf("Wood Box: Filled via API on '$p' using action '$act'"); return true }
                            }
                        }
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            val getItems = try { cls.getMethod("getItems") } catch (_: Throwable) { null }
            val items = if (getItems != null) (getItems.invoke(null) as? Collection<*>)?.toList() ?: emptyList() else emptyList()
            for (it in items) {
                val iname = try { it!!::class.java.getMethod("getName").invoke(it) as? String } catch (_: Throwable) { null }
                    ?: try { it!!::class.java.getField("name").get(it) as? String } catch (_: Throwable) { null }
                    ?: continue
                if (patterns.any { p -> iname.contains(p, ignoreCase = true) }) {
                    // Try declared actions if any
                    val opts: List<String> = try {
                        val mo = it!!::class.java.methods.firstOrNull { m -> m.name.lowercase().contains("option") && m.parameterCount == 0 }
                        val o = mo?.invoke(it)
                        when (o) {
                            is Array<*> -> o.filterIsInstance<String>()
                            is Collection<*> -> o.filterIsInstance<String>()
                            else -> emptyList()
                        }
                    } catch (_: Throwable) { emptyList() }
                    val preferred = (actions + opts).distinct()
                    for (act in preferred) {
                        try {
                            val interact = cls.getMethod("interact", String::class.java, String::class.java)
                            val ok = interact.invoke(null, iname, act)
                            if (ok is Boolean && ok) { inf("Wood Box: Filled via API on '$iname' using action '$act'"); return true }
                        } catch (_: Throwable) { }
                    }
                    // As a last resort, try regex-based interact if available
                    try {
                        val patternClass = Class.forName("java.util.regex.Pattern")
                        val compile = patternClass.getMethod("compile", String::class.java)
                        val pName = compile.invoke(null, "(?i).*" + java.util.regex.Pattern.quote(iname) + ".*")
                        val pAct = compile.invoke(null, "(?i).*fill.*")
                        val m = cls.getMethod("interact", patternClass, patternClass)
                        val ok = m.invoke(null, pName, pAct)
                        if (ok is Boolean && ok) { inf("Wood Box: Filled via API (regex) on '$iname'"); return true }
                    } catch (_: Throwable) { }
                }
            }
        } catch (_: Throwable) { }
        // XAPI fallback (scan items and attempt actions)
        try {
            val cls = Class.forName("net.botwithus.xapi.game.inventory.Backpack")
            // Fast path: contains(name) then interact(name, "Fill")
            try {
                val contains = cls.getMethod("contains", String::class.java)
                val interact = cls.getMethod("interact", String::class.java, String::class.java)
                for (p in patterns) {
                    try {
                        val has = contains.invoke(null, p) as? Boolean
                        if (has == true) {
                            for (act in actions) {
                                val ok = interact.invoke(null, p, act)
                                if (ok is Boolean && ok) { inf("Wood Box: Filled via XAPI on '$p' using action '$act'"); return true }
                            }
                        }
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            val getItems = try { cls.getMethod("getItems") } catch (_: Throwable) { null }
            val items = if (getItems != null) (getItems.invoke(null) as? Collection<*>)?.toList() ?: emptyList() else emptyList()
            for (it in items) {
                val iname = try { it!!::class.java.getMethod("getName").invoke(it) as? String } catch (_: Throwable) { null }
                    ?: try { it!!::class.java.getField("name").get(it) as? String } catch (_: Throwable) { null }
                    ?: continue
                if (patterns.any { p -> iname.contains(p, ignoreCase = true) }) {
                    val opts: List<String> = try {
                        val mo = it!!::class.java.methods.firstOrNull { m -> m.name.lowercase().contains("option") && m.parameterCount == 0 }
                        val o = mo?.invoke(it)
                        when (o) {
                            is Array<*> -> o.filterIsInstance<String>()
                            is Collection<*> -> o.filterIsInstance<String>()
                            else -> emptyList()
                        }
                    } catch (_: Throwable) { emptyList() }
                    val preferred = (actions + opts).distinct()
                    for (act in preferred) {
                        try {
                            val interact = cls.getMethod("interact", String::class.java, String::class.java)
                            val ok = interact.invoke(null, iname, act)
                            if (ok is Boolean && ok) { inf("Wood Box: Filled via XAPI on '$iname' using action '$act'"); return true }
                        } catch (_: Throwable) { }
                    }
                    try {
                        val patternClass = Class.forName("java.util.regex.Pattern")
                        val compile = patternClass.getMethod("compile", String::class.java)
                        val pName = compile.invoke(null, "(?i).*" + java.util.regex.Pattern.quote(iname) + ".*")
                        val pAct = compile.invoke(null, "(?i).*fill.*")
                        val m = cls.getMethod("interact", patternClass, patternClass)
                        val ok = m.invoke(null, pName, pAct)
                        if (ok is Boolean && ok) { inf("Wood Box: Filled via XAPI (regex) on '$iname'"); return true }
                    } catch (_: Throwable) { }
                }
            }
        } catch (_: Throwable) { }
        warn("Wood Box: No wood box item found in backpack")
        return false
    }

    // Lightweight location model (name + which trees are supported there)
    data class TreeLocation(
        val name: String,
        val availableTrees: List<String>,
        val chop: net.botwithus.rs3.world.Coordinate? = null,
        val bank: net.botwithus.rs3.world.Coordinate? = null
    )

    // Location catalog (names adapted from external/UberChop; coordinates omitted here)
    // Filtering uses case-insensitive contains to tolerate naming differences (e.g., "Magic" vs "Magic tree").
    val treeLocations: List<TreeLocation> = listOf(
        TreeLocation(
            name = "Burthorpe",
            availableTrees = listOf("Tree", "Oak"),
            chop = net.botwithus.rs3.world.Coordinate(2901, 3505, 0),
            bank = net.botwithus.rs3.world.Coordinate(2891, 3539, 0)
        ),
        TreeLocation(
            name = "Draynor Village",
            availableTrees = listOf("Tree", "Oak", "Willow", "Elder tree"),
            chop = net.botwithus.rs3.world.Coordinate(3104, 3238, 0),
            bank = net.botwithus.rs3.world.Coordinate(3093, 3243, 0)
        ),
        TreeLocation(
            name = "Eagles' Peak",
            availableTrees = listOf("Eternal magic tree"),
            chop = net.botwithus.rs3.world.Coordinate(2329, 3583, 0),
            bank = net.botwithus.rs3.world.Coordinate(2283, 3554, 0)
        ),
        TreeLocation(
            name = "Edgeville",
            availableTrees = listOf("Tree", "Oak", "Willow", "Yew"),
            chop = net.botwithus.rs3.world.Coordinate(3093, 3492, 0),
            bank = net.botwithus.rs3.world.Coordinate(2853, 2955, 0)
        ),
        TreeLocation(
            name = "South Edgeville",
            availableTrees = listOf("Elder tree"),
            chop = net.botwithus.rs3.world.Coordinate(3089, 3456, 0),
            bank = net.botwithus.rs3.world.Coordinate(2853, 2955, 0)
        ),
        TreeLocation(
            name = "Falador",
            availableTrees = listOf("Tree", "Oak", "Yew", "Elder tree"),
            chop = net.botwithus.rs3.world.Coordinate(3006, 3311, 0),
            bank = net.botwithus.rs3.world.Coordinate(2958, 3297, 0)
        ),
        TreeLocation(
            name = "Clan Camp",
            availableTrees = listOf("Tree", "Oak", "Yew"),
            chop = net.botwithus.rs3.world.Coordinate(2967, 3295, 0),
            bank = net.botwithus.rs3.world.Coordinate(2957, 3296, 0)
        ),
        TreeLocation(
            name = "Rimmington",
            availableTrees = listOf("Tree", "Oak", "Yew", "Elder tree"),
            chop = net.botwithus.rs3.world.Coordinate(2942, 3231, 0),
            bank = net.botwithus.rs3.world.Coordinate(2957, 3296, 0)
        ),
        TreeLocation(
            name = "Kharazi Jungle - Mahogany",
            availableTrees = listOf("Mahogany"),
            chop = net.botwithus.rs3.world.Coordinate(2781, 2952, 0),
            bank = net.botwithus.rs3.world.Coordinate(2853, 2954, 0)
        ),
        TreeLocation(
            name = "Kharazi Jungle - Teak",
            availableTrees = listOf("Teak"),
            chop = net.botwithus.rs3.world.Coordinate(2835, 2911, 0),
            bank = net.botwithus.rs3.world.Coordinate(2853, 2954, 0)
        ),
        TreeLocation(
            name = "Lumbridge",
            availableTrees = listOf("Tree", "Oak", "Willow"),
            chop = net.botwithus.rs3.world.Coordinate(3244, 3225, 0),
            bank = net.botwithus.rs3.world.Coordinate(3217, 3260, 0)
        ),
        TreeLocation(
            name = "Lumbridge Market",
            availableTrees = listOf("Tree", "Oak", "Yew"),
            chop = net.botwithus.rs3.world.Coordinate(3196, 3270, 0),
            bank = net.botwithus.rs3.world.Coordinate(3217, 3260, 0)
        ),
        TreeLocation(
            name = "Menaphos",
            availableTrees = listOf("Acadia tree"),
            chop = net.botwithus.rs3.world.Coordinate(3216, 2723, 0),
            bank = net.botwithus.rs3.world.Coordinate(3234, 2759, 0)
        ),
        TreeLocation(
            name = "Seers' Village",
            availableTrees = listOf("Tree", "Oak", "Willow", "Maple Tree", "Yew", "Magic tree", "Elder tree"),
            chop = net.botwithus.rs3.world.Coordinate(2726, 3435, 0),
            bank = net.botwithus.rs3.world.Coordinate(2724, 3481, 0)
        ),
        TreeLocation(
            name = "Sinclair Manor",
            availableTrees = listOf("Maple Tree"),
            chop = net.botwithus.rs3.world.Coordinate(2722, 3559, 0),
            bank = net.botwithus.rs3.world.Coordinate(2724, 3481, 0)
        ),
        TreeLocation(
            name = "Legends Guild",
            availableTrees = listOf("Tree", "Oak", "Maple Tree", "Magic tree"),
            chop = net.botwithus.rs3.world.Coordinate(2690, 3364, 0),
            bank = net.botwithus.rs3.world.Coordinate(2676, 3404, 0)
        ),
        TreeLocation(
            name = "Tai Bwo Wannai",
            availableTrees = listOf("Teak", "Mahogany"),
            chop = net.botwithus.rs3.world.Coordinate(2816, 3084, 0),
            bank = net.botwithus.rs3.world.Coordinate(2891, 3538, 0)
        ),
        TreeLocation(
            name = "Uzer",
            availableTrees = listOf("Tree", "Teak"),
            chop = net.botwithus.rs3.world.Coordinate(3506, 3075, 0),
            bank = net.botwithus.rs3.world.Coordinate(3306, 3121, 0)
        ),
        TreeLocation(
            name = "Catherby",
            availableTrees = listOf("Tree", "Oak", "Yew"),
            chop = net.botwithus.rs3.world.Coordinate(2784, 3439, 0),
            bank = net.botwithus.rs3.world.Coordinate(2796, 3440, 0)
        ),
        TreeLocation(
            name = "Al Kharid",
            availableTrees = listOf("Acadia tree"),
            chop = net.botwithus.rs3.world.Coordinate(3299, 3238, 0),
            bank = net.botwithus.rs3.world.Coordinate(3272, 3165, 0)
        ),
        TreeLocation(
            name = "Mage Training Arena",
            availableTrees = listOf("Magic tree"),
            chop = net.botwithus.rs3.world.Coordinate(3362, 3297, 0),
            bank = net.botwithus.rs3.world.Coordinate(3379, 3270, 0)
        ),
        TreeLocation(
            name = "North Uzer",
            availableTrees = listOf("Acadia tree"),
            chop = net.botwithus.rs3.world.Coordinate(3468, 3126, 0),
            bank = net.botwithus.rs3.world.Coordinate(3305, 3119, 0)
        ),
        TreeLocation(
            name = "Musa Point",
            availableTrees = listOf("Teak"),
            chop = net.botwithus.rs3.world.Coordinate(2901, 3157, 0),
            bank = net.botwithus.rs3.world.Coordinate(2891, 3539, 0)
        ),
        TreeLocation(
            name = "Varrock",
            availableTrees = listOf("Tree", "Oak", "Willow", "Maple Tree", "Elder tree"),
            chop = net.botwithus.rs3.world.Coordinate(3223, 3363, 0),
            bank = net.botwithus.rs3.world.Coordinate(3211, 3370, 0)
        ),
        // Ivy locations (wall ivy around cities)
        TreeLocation(
            name = "Varrock Palace - Ivy",
            availableTrees = listOf("Ivy")
        ),
        TreeLocation(
            name = "Falador West Wall - Ivy",
            availableTrees = listOf("Ivy")
        ),
        TreeLocation(
            name = "Ardougne - Ivy",
            availableTrees = listOf("Ivy")
        ),
        TreeLocation(
            name = "Yanille - Ivy",
            availableTrees = listOf("Ivy")
        ),
        // Bamboo (Arc/Anachronia themed)
        TreeLocation(
            name = "Anachronia - Bamboo Grove",
            availableTrees = listOf("Bamboo")
        ),
        TreeLocation(
            name = "Waiko - Bamboo",
            availableTrees = listOf("Bamboo")
        ),
        // Crystal tree (rotating active site; provide generic entries)
        TreeLocation(
            name = "Prifddinas - Crystal tree",
            availableTrees = listOf("Crystal", "Crystal tree")
        ),
        TreeLocation(
            name = "Crystal Tree - Rotating Site",
            availableTrees = listOf("Crystal", "Crystal tree")
        ),
        // Arctic pine (Fremennik Isles)
        TreeLocation(
            name = "Neitiznot - Arctic pine",
            availableTrees = listOf("Arctic pine")
        )
    )
}

