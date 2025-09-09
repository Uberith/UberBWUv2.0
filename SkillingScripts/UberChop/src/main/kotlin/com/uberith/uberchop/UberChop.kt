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
    var bankWhenFull: Boolean = false
    var logsChopped: Int = 0
    var status: String = "Idle"
    private var startEpochMs: Long = 0L

    private var currentTarget: SceneObject? = null
    private var lastTargetTs: Long = 0L
    private var interactFailCount: Int = 0
    private val retargetCooldownMs: Long = 1500L
    private val maxInteractFailsBeforeRetarget = 2

    enum class Phase { READY, PREPARING, CHOPPING, BANKING }
    @Volatile var phase: Phase = Phase.READY

    private val gui = UberChopGUI(this)
    private val log = LoggerFactory.getLogger(UberChop::class.java)

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        gui.render(workspace)
    }

    override fun onActivation() {
        super.onActivation()
        // Initialize targetTree from saved settings
        try {
            val idx = settings.savedTreeType.coerceIn(0, TreeTypes.ALL.size - 1)
            targetTree = TreeTypes.ALL[idx]
        } catch (_: Throwable) { /* keep default */ }
        log.info("UberChop activated")
        status = "Active - Preparing"
        phase = Phase.PREPARING
        // Preload GUI assets (e.g., logo texture)
        try { gui.preload() } catch (_: Throwable) {}
        if (startEpochMs == 0L) startEpochMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        super.onDeactivation()
        log.info("UberChop deactivated")
        status = "Inactive"
        phase = Phase.READY
    }

    override suspend fun onLoop() {
        when (phase) {
            Phase.READY -> {
                status = "Starting..."; phase = Phase.PREPARING; awaitTicks(1)
            }
            Phase.PREPARING -> {
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
                val opened = openNearestBankRs3()
                awaitTicks(3)
                if (!opened) { status = "Banking: open failed"; awaitTicks(2); phase = Phase.CHOPPING; return }

            // Empty wood box if option present
            tryEmptyWoodBoxXapi()

            // Deposit only logs + nests using XAPI (reflection)
            xapiBankDepositOnlyLogsAndNests()
            awaitTicks(2)
            xapiBankClose()
            awaitTicks(2)
            phase = Phase.CHOPPING
            }
            Phase.CHOPPING -> {
                if (isAnyInventoryFull()) {
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
                    if (tree != null) { currentTarget = tree; lastTargetTs = now; interactFailCount = 0 }
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

    private fun openNearestBankRs3(): Boolean {
        val player = LocalPlayer.self() ?: return false
        val so = World.getSceneObjects()
            .asSequence()
            .filter { it.isHidden() == false }
            .filter {
                val name = it.name.lowercase(Locale.ROOT)
                name.contains("bank") || it.getOptions().any { o -> o != null && o.lowercase(Locale.ROOT).contains("bank") }
            }
            .minByOrNull { player.distanceTo(it.coordinate) } ?: return false
        val opts = so.getOptions()
        val idx = opts.indexOfFirst { it != null && it.lowercase(Locale.ROOT).contains("bank") }
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
        val idx = kotlin.random.Random.nextInt(candidates.size)
        return candidates[idx]
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
        val ms = if (startEpochMs == 0L) 0L else (System.currentTimeMillis() - startEpochMs)
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
    fun logsPerHour(): Int {
        val ms = (if (startEpochMs == 0L) 1L else (System.currentTimeMillis() - startEpochMs)).coerceAtLeast(1L)
        val perMs = logsChopped.toDouble() / ms.toDouble()
        return (perMs * 3_600_000.0).toInt()
    }
    private fun isAnyInventoryFull(): Boolean {
        return InventoryManager.getInventories().any { inv ->
            try { inv.isFull() } catch (_: Throwable) { false }
        }
    }
}

