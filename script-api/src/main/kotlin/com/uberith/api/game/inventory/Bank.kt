package com.uberith.api.game.inventory

import com.uberith.api.SuspendableScript
import net.botwithus.rs3.interfaces.Component
import net.botwithus.rs3.inventories.Inventory
import net.botwithus.rs3.inventories.InventoryManager
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.rs3.item.Item
import net.botwithus.rs3.minimenu.Action
import net.botwithus.rs3.minimenu.MiniMenu
import net.botwithus.rs3.vars.VarDomain
import net.botwithus.rs3.world.Distance
import net.botwithus.util.Rand
import net.botwithus.xapi.query.ComponentQuery
import net.botwithus.xapi.query.InventoryItemQuery
import net.botwithus.xapi.query.NpcQuery
import net.botwithus.xapi.query.SceneObjectQuery
import net.botwithus.xapi.query.result.ResultSet
import net.botwithus.xapi.script.permissive.base.PermissiveScript
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.BiFunction
import java.util.regex.Pattern
import java.util.stream.Collectors

object Bank {
    private const val PRESET_BROWSING_VARBIT_ID = 49662
    private const val SELECTED_OPTIONS_TAB_VARBIT_ID = 45191
    private const val WITHDRAW_TYPE_VARBIT_ID = 45189
    private const val WITHDRAW_X_VARP_ID = 111

    private val BANK_NAME_PATTERN: Pattern = Pattern.compile("^(?!.*deposit).*(bank|counter).*$", Pattern.CASE_INSENSITIVE)
    private const val LAST_PRESET_OPTION = "Load Last Preset from"
    private const val INVENTORY_ID = 95
    private const val INTERFACE_INDEX = 517
    private const val COMPONENT_INDEX = 202

    private val logger: Logger = LoggerFactory.getLogger(Bank::class.java)

    private var previousLoadedPreset = -1

    fun open(script: PermissiveScript): Boolean {
        return try {
            logger.info("Attempting find bank obj")
            val objUse = SceneObjectQuery.newQuery().name(BANK_NAME_PATTERN).option("Use").results().nearest()
            val objBank = SceneObjectQuery.newQuery().name(BANK_NAME_PATTERN).option("Bank").results().nearest()
            val chest = SceneObjectQuery.newQuery().name("Shantay chest").results().nearest()
            val obj = listOfNotNull(objUse, objBank, chest).minByOrNull { Distance.to(it) }

            logger.info("Attempting find bank npc")
            val npc = NpcQuery.newQuery().option("Bank").results().nearest()
            logger.info("Bank opening initiated")
            var useObj = true

            logger.info("Object is ${if (obj != null) "not null" else "null"}")
            logger.info("Npc is ${if (npc != null) "not null" else "null"}")

            if (obj != null && npc != null) {
                logger.info("Distance.to(obj): ${Distance.to(obj)}")
                logger.info("Distance.to(npc): ${Distance.to(npc)}")
                val objDist = Distance.to(obj)
                val npcDist = Distance.to(npc)
                if (!objDist.isNaN() && !npcDist.isNaN()) {
                    useObj = objDist < npcDist
                }
                logger.info("useObj: $useObj")
            }

            if (obj != null && useObj) {
                script.info("Interacting via Object: ${obj.name}")
                val actions = obj.options
                logger.info("Available Options: $actions")
                if (!actions.isEmpty()) {
                    val action = actions.stream().filter { it != null && it.isNotEmpty() }.findFirst()
                    logger.info("action.isPresent(): ${action.isPresent}")
                    if (action.isPresent) {
                        logger.info("Attempting to interact with bank object using action: ${action.get()}")
                        val interactionResult = obj.interact(action.get())
                        script.info("Object interaction completed: $interactionResult")
                        interactionResult > 0
                    } else {
                        script.warn("No valid action found for bank object")
                        false
                    }
                } else {
                    script.warn("No options available on bank object")
                    false
                }
            } else if (npc != null) {
                script.info("Interacting via NPC")
                val interactionResult = npc.interact("Bank")
                script.info("NPC interaction completed: $interactionResult")
                interactionResult > 0
            } else {
                script.warn("No valid bank object or NPC found")
                false
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
            false
        }
    }

    fun isOpen(): Boolean {
        // Prefer the official Interfaces.isOpen via reflection (avoids hard compile dependency)
        try {
            val cls = Class.forName("net.botwithus.rs3.interfaces.Interfaces")
            val m = cls.getMethod("isOpen", Int::class.javaPrimitiveType)
            val open = (m.invoke(null, INTERFACE_INDEX) as? Boolean) ?: false
            logger.debug("[Bank] isOpen via Interfaces.isOpen({}) -> {}", INTERFACE_INDEX, open)
            return open
        } catch (t: Throwable) {
            logger.debug("[Bank] Interfaces.isOpen reflection failed: {}", t.message)
        }

        // Fallback: presence of a known bank component under interface 517
        return try {
            val comp = ComponentQuery.newQuery(INTERFACE_INDEX).id(COMPONENT_INDEX).results().first()
            val open = comp != null
            logger.debug("[Bank] isOpen via component probe (iface={}, compId={}) -> {}", INTERFACE_INDEX, COMPONENT_INDEX, open)
            open
        } catch (t: Throwable) {
            logger.debug("[Bank] isOpen component probe failed: {}", t.message)
            false
        }
    }
    fun close(): Boolean = MiniMenu.doAction(Action.COMPONENT, 1, -1, 33882430) > 0

    fun getInventory(): Inventory? = InventoryManager.getInventory(INVENTORY_ID)

    fun loadLastPreset(): Boolean {
        val obj = SceneObjectQuery.newQuery().option(LAST_PRESET_OPTION).results().nearest()
        val npc = NpcQuery.newQuery().option(LAST_PRESET_OPTION).results().nearest()
        var useObj = true

        if (obj != null && npc != null) {
            val objDist = Distance.to(obj)
            val npcDist = Distance.to(npc)
            if (!objDist.isNaN() && !npcDist.isNaN())
                useObj = objDist < npcDist
        }
        return if (obj != null && useObj) {
            obj.interact(LAST_PRESET_OPTION) > 0
        } else if (npc != null) {
            npc.interact(LAST_PRESET_OPTION) > 0
        } else false
    }

    fun getItems(): Array<Item> =
        InventoryItemQuery.newQuery(INVENTORY_ID).results()
            .stream().filter { it.id != -1 }
            .map { it as Item }
            .toList()
            .toTypedArray()

    fun count(results: ResultSet<InventoryItem>): Int = results.stream().mapToInt(Item::getQuantity).sum()

    fun first(query: InventoryItemQuery): Item? = query.results().first()

    fun isEmpty(): Boolean = getItems().isEmpty()

    fun interact(slot: Int, option: Int): Boolean {
        val results: ResultSet<InventoryItem> = InventoryItemQuery.newQuery(INVENTORY_ID).slot(slot).results()
        val item = results.first()
        if (item != null) {
            logger.info("[Inventory#interact(slot, option)]: ${item.id}")
            val queryResults = ComponentQuery.newQuery(INTERFACE_INDEX).id(COMPONENT_INDEX).itemId(item.id).results()
            logger.info("[Inventory#interact(slot, option)]: QueryResults: ${queryResults.size()}")
            val result = queryResults.first()
            return result != null && result.interact(option) > 0
        }
        return false
    }

    fun contains(query: InventoryItemQuery): Boolean = count(query.results()) > 0

    fun contains(vararg itemNames: String): Boolean =
        !InventoryItemQuery.newQuery(INVENTORY_ID).name(*itemNames).results().isEmpty

    fun contains(itemNamePattern: Pattern): Boolean =
        !InventoryItemQuery.newQuery(INVENTORY_ID).name(itemNamePattern).results().isEmpty

    fun getCount(vararg itemNames: String): Int =
        count(InventoryItemQuery.newQuery(INVENTORY_ID).name(*itemNames).results())

    fun getCount(namePattern: Pattern): Int =
        count(InventoryItemQuery.newQuery(INVENTORY_ID).name(namePattern).results())

    fun withdraw(query: InventoryItemQuery, option: Int): Boolean {
        setTransferOption(TransferOptionType.ALL)
        val item = query.results().first()
        if (item != null) {
            logger.info("Item: ${item.name}")
        } else {
            logger.info("Item is null")
        }
        return item != null && interact(item.slot, option)
    }

    fun withdraw(itemName: String?, option: Int): Boolean {
        if (itemName != null && itemName.isNotEmpty()) {
            return withdraw(InventoryItemQuery.newQuery(INVENTORY_ID).name(itemName), option)
        }
        return false
    }

    fun withdraw(itemId: Int, option: Int): Boolean {
        return if (itemId >= 0) {
            withdraw(InventoryItemQuery.newQuery(INVENTORY_ID).id(itemId), option)
        } else false
    }

    fun withdraw(pattern: Pattern?, option: Int): Boolean {
        return if (pattern != null) {
            withdraw(InventoryItemQuery.newQuery(INVENTORY_ID).name(pattern), option)
        } else false
    }

    fun withdrawAll(name: String): Boolean = withdraw(InventoryItemQuery.newQuery(INVENTORY_ID).name(name), 1)
    fun withdrawAll(id: Int): Boolean = withdraw(InventoryItemQuery.newQuery(INVENTORY_ID).id(id), 1)
    fun withdrawAll(pattern: Pattern): Boolean = withdraw(InventoryItemQuery.newQuery(INVENTORY_ID).name(pattern), 1)

    fun depositAll(): Boolean {
        setTransferOption(TransferOptionType.ALL)
        val comp = ComponentQuery.newQuery(INTERFACE_INDEX).option("Deposit carried items").results().first()
        return comp != null && comp.interact(1) > 0
    }

    fun depositEquipment(): Boolean {
        val component = ComponentQuery.newQuery(INTERFACE_INDEX).id(42).results().first()
        return component != null && component.interact(1) > 0
    }

    fun depositBackpack(): Boolean {
        val component = ComponentQuery.newQuery(INTERFACE_INDEX).id(39).results().first()
        return component != null && component.interact(1) > 0
    }

    fun deposit(script: PermissiveScript, query: ComponentQuery, option: Int): Boolean {
        val item = query.results().first()
        return deposit(script, item, option)
    }

    fun depositAll(script: PermissiveScript, query: ComponentQuery): Boolean {
        val item = query.results().first()
        return deposit(script, item, 1)
    }

    fun deposit(script: PermissiveScript, comp: Component?, option: Int): Boolean {
        setTransferOption(TransferOptionType.ALL)
        val valRes = comp != null && comp.interact(option) > 0
        if (valRes) script.delay(Rand.nextInt(1, 2))
        return valRes
    }

    fun depositAll(script: PermissiveScript, vararg itemNames: String): Boolean {
        return !InventoryItemQuery.newQuery(93).name(*itemNames).results().stream()
            .map { it.id }.distinct()
            .map { i: Int -> depositAll(script, ComponentQuery.newQuery(517).itemId(i)) }
            .toList().contains(false)
    }

    fun depositAll(script: PermissiveScript, vararg itemIds: Int): Boolean {
        return !InventoryItemQuery.newQuery(93).id(*itemIds).results().stream()
            .map { it.id }.distinct()
            .map { i: Int -> depositAll(script, ComponentQuery.newQuery(517).itemId(i)) }
            .toList().contains(false)
    }

    fun depositAll(script: PermissiveScript, vararg patterns: Pattern): Boolean {
        return !InventoryItemQuery.newQuery(93).name(*patterns).results().stream()
            .map { it.id }.distinct()
            .map { i: Int -> depositAll(script, ComponentQuery.newQuery(517).itemId(i)) }
            .toList().contains(false)
    }

    // Empty-Box helpers (e.g., "Empty - logs and bird's nests") within the Bank interface
    fun emptyBox(option: String): Boolean {
        if (!isOpen()) {
            logger.info("[Bank] emptyBox(option='{}'): bank not open", option)
            return false
        }
        val comp = ComponentQuery.newQuery(INTERFACE_INDEX).option(option).results().first()
        val ok = comp != null && (comp.interact(1) > 0 || comp.interact(option) > 0)
        logger.info(
            "[Bank] emptyBox(option='{}'): component {} -> {}",
            option,
            if (comp != null) "found" else "not-found",
            ok
        )
        return ok
    }

    fun emptyBox(script: PermissiveScript, option: String): Boolean {
        val ok = emptyBox(option)
        if (ok) script.delay(Rand.nextInt(1, 2))
        return ok
    }

    suspend fun emptyBox(script: SuspendableScript, option: String): Boolean {
        val ok = emptyBox(option)
        if (ok) script.awaitTicks(2)
        return ok
    }

    // Overloads that specify the box name and fall back to backpack interaction when needed
    fun emptyBox(boxName: String, option: String): Boolean {
        if (!isOpen()) {
            logger.info("[Bank] emptyBox(name='{}', option='{}'): bank not open", boxName, option)
            return false
        }
        try {
            // 1) Target the specific item-id component inside the bank interface (most reliable)
            val bpItem = Backpack.getItem({ n, h -> h.toString().contains(n, true) }, boxName)
            logger.info(
                "[Bank] emptyBox(name='{}', option='{}'): backpack item -> {}",
                boxName,
                option,
                bpItem?.let { "${it.name} (${it.id})" } ?: "null"
            )
            if (bpItem != null) {
                val comp = ComponentQuery.newQuery(INTERFACE_INDEX).id(COMPONENT_INDEX).itemId(bpItem.id).results().first()
                logger.debug(
                    "[Bank] emptyBox(name='{}'): bank component by itemId {}",
                    boxName,
                    if (comp != null) "found" else "not-found"
                )
                if (comp != null) {
                    val ok = comp.interact(1) > 0 || comp.interact(option) > 0
                    logger.info("[Bank] emptyBox(name='{}'): interact component by id -> {}", boxName, ok)
                    if (ok) return true
                }
            }
            // 2) Try any component with the given option under the bank interface
            val anyComp = ComponentQuery.newQuery(INTERFACE_INDEX).option(option).results().first()
            logger.debug(
                "[Bank] emptyBox(name='{}'): bank component by option '{}' {}",
                boxName,
                option,
                if (anyComp != null) "found" else "not-found"
            )
            if (anyComp != null && (anyComp.interact(1) > 0 || anyComp.interact(option) > 0)) {
                logger.info("[Bank] emptyBox(name='{}'): interact component by option -> true", boxName)
                return true
            }
            // 3) Last-resort: interact with the backpack item directly using the provided option
            val ok = if (bpItem != null) Backpack.interact(bpItem, option) else false
            logger.info("[Bank] emptyBox(name='{}'): fallback backpack interact -> {}", boxName, ok)
            return ok
        } catch (t: Throwable) {
            logger.warn("[Bank] emptyBox(name='{}') exception: {}", boxName, t.message)
            return false
        }
    }

    fun emptyBox(script: PermissiveScript, boxName: String, option: String): Boolean {
        val ok = emptyBox(boxName, option)
        if (ok) script.delay(Rand.nextInt(1, 2))
        return ok
    }

    suspend fun emptyBox(script: SuspendableScript, boxName: String, option: String): Boolean {
        val ok = emptyBox(boxName, option)
        if (ok) script.awaitTicks(2)
        return ok
    }

    fun depositAllExcept(script: PermissiveScript, vararg itemNames: String): Boolean {
        val keepNames = HashSet(listOf(*itemNames))
        val keepIds = Backpack.getItems().stream()
            .filter { it.name != null && keepNames.contains(it.name) }
            .map(Item::getId)
            .collect(Collectors.toSet())

        val items = ComponentQuery.newQuery(517).results().stream()
            .filter { i -> !keepIds.contains(i.itemId) && (i.options.contains("Deposit-All") || i.options.contains("Deposit-1")) }
            .map(Component::getItemId)
            .collect(Collectors.toSet())

        return !items.stream().map { i: Int -> depositAll(script, ComponentQuery.newQuery(517).itemId(i)) }.toList().contains(false)
    }

    fun depositAllExcept(script: PermissiveScript, vararg ids: Int): Boolean {
        val idSet = Arrays.stream(ids).boxed().collect(Collectors.toSet())
        val items = ComponentQuery.newQuery(517).results().stream()
            .filter { i -> !idSet.contains(i.itemId) && (i.options.contains("Deposit-All") || i.options.contains("Deposit-1")) }
            .map(Component::getItemId)
            .collect(Collectors.toSet())
        return !items.stream().map { i: Int -> depositAll(script, ComponentQuery.newQuery(517).itemId(i)) }.toList().contains(false)
    }

    fun depositAllExcept(script: PermissiveScript, vararg patterns: Pattern): Boolean {
        val idMap = Backpack.getItems().stream()
            .filter { i: Item -> i.name != null && Arrays.stream(patterns).anyMatch { p: Pattern -> p.matcher(i.name).matches() } }
            .collect(Collectors.toMap(Item::getId, Item::getName))
        val items = ComponentQuery.newQuery(517).results().stream()
            .filter { i -> !idMap.containsKey(i.itemId) && (i.options.contains("Deposit-All") || i.options.contains("Deposit-1")) }
            .map(Component::getItemId)
            .collect(Collectors.toSet())
        return !items.stream().map { i: Int -> depositAll(script, ComponentQuery.newQuery(517).itemId(i)) }.toList().contains(false)
    }

    fun deposit(script: PermissiveScript, itemId: Int, option: Int): Boolean =
        deposit(script, ComponentQuery.newQuery(517).itemId(itemId), option)

    fun deposit(script: PermissiveScript, name: String, spred: BiFunction<String, CharSequence, Boolean>, option: Int): Boolean =
        deposit(script, ComponentQuery.newQuery(517).itemName(name, spred), option)

    fun deposit(script: PermissiveScript, name: String, option: Int): Boolean =
        deposit(script, name, BiFunction { a, b -> a.contentEquals(b) }, option)

    fun loadPreset(script: PermissiveScript, presetNumber: Int): Boolean {
        val presetBrowsingValue = VarDomain.getVarBitValue(PRESET_BROWSING_VARBIT_ID)
        if ((presetNumber >= 10 && presetBrowsingValue < 1) || (presetNumber < 10 && presetBrowsingValue > 0)) {
            MiniMenu.doAction(Action.COMPONENT, 1, 100, 33882231)
            script.delay(Rand.nextInt(1, 2))
        }
        val result = MiniMenu.doAction(Action.COMPONENT, 1, presetNumber % 9, 33882231) > 0
        if (result) {
            previousLoadedPreset = presetNumber
        }
        return result
    }

    fun getVarbitValue(slot: Int, varbitId: Int): Int {
        val inventory: Inventory? = getInventory()
        return inventory?.getVarbitValue(slot, varbitId) ?: Int.MIN_VALUE
    }

    fun setTransferOption(transferoptionType: TransferOptionType): Boolean {
        val depositOptionState = VarDomain.getVarBitValue(WITHDRAW_TYPE_VARBIT_ID)
        return depositOptionState == transferoptionType.varbitStateValue ||
                MiniMenu.doAction(Action.COMPONENT, 1, -1, 33882215) > 0
    }

    fun getPreviousLoadedPreset(): Int = previousLoadedPreset

    // Suspendable overloads for coroutine-based scripts
    suspend fun open(script: SuspendableScript): Boolean {
        val opened = open(object : PermissiveScript() {})
        if (opened) script.awaitUntil(10) { isOpen() }
        return isOpen()
    }

    suspend fun close(script: SuspendableScript): Boolean {
        val res = close()
        if (res) script.awaitUntil(5) { !isOpen() }
        return !isOpen()
    }

    suspend fun loadLastPreset(script: SuspendableScript): Boolean {
        val res = loadLastPreset()
        if (res) script.awaitTicks(2)
        return res
    }

    suspend fun loadPreset(script: SuspendableScript, presetNumber: Int): Boolean {
        val res = loadPreset(object : PermissiveScript() {}, presetNumber)
        if (res) script.awaitTicks(2)
        return res
    }

    suspend fun depositAll(script: SuspendableScript): Boolean {
        val res = depositAll()
        if (res) script.awaitTicks(1)
        return res
    }

    /** Suspendable variant: deposit a single component by option. */
    suspend fun deposit(script: SuspendableScript, comp: Component?, option: Int): Boolean {
        setTransferOption(TransferOptionType.ALL)
        val ok = comp != null && comp.interact(option) > 0
        if (ok) script.awaitTicks(1)
        return ok
    }

    /** Suspendable variant: deposit by a component query using option 1 (Deposit-All/Deposit-1). */
    suspend fun depositAll(script: SuspendableScript, query: ComponentQuery): Boolean {
        val item = query.results().first()
        return deposit(script, item, 1)
    }

    /** Suspendable variant: deposit by name(s). */
    suspend fun depositAll(script: SuspendableScript, vararg itemNames: String): Boolean {
        val ids = InventoryItemQuery.newQuery(93).name(*itemNames).results().stream()
            .map { it.id }.distinct().toList()
        for (id in ids) {
            if (!depositAll(script, ComponentQuery.newQuery(517).itemId(id))) return false
        }
        return ids.isNotEmpty()
    }

    /** Suspendable variant: deposit by id(s). */
    suspend fun depositAll(script: SuspendableScript, vararg itemIds: Int): Boolean {
        val ids = InventoryItemQuery.newQuery(93).id(*itemIds).results().stream()
            .map { it.id }.distinct().toList()
        for (id in ids) {
            if (!depositAll(script, ComponentQuery.newQuery(517).itemId(id))) return false
        }
        return ids.isNotEmpty()
    }

    /** Suspendable variant: deposit by regex pattern(s). */
    suspend fun depositAll(script: SuspendableScript, vararg patterns: Pattern): Boolean {
        val ids = InventoryItemQuery.newQuery(93).name(*patterns).results().stream()
            .map { it.id }.distinct().toList()
        for (id in ids) {
            if (!depositAll(script, ComponentQuery.newQuery(517).itemId(id))) return false
        }
        return ids.isNotEmpty()
    }

    /**
     * Suspendable variant: deposit everything except matching names.
     * Selects all bank component entries that support Deposit-All/Deposit-1 and are not in the keep-set.
     */
    suspend fun depositAllExcept(script: SuspendableScript, vararg itemNames: String): Boolean {
        val keepNames = HashSet(listOf(*itemNames))
        val keepIds = Backpack.getItems().stream()
            .filter { it.name != null && keepNames.contains(it.name) }
            .map(Item::getId)
            .collect(Collectors.toSet())

        val itemIds = ComponentQuery.newQuery(517).results().stream()
            .filter { i -> !keepIds.contains(i.itemId) && (i.options.contains("Deposit-All") || i.options.contains("Deposit-1")) }
            .map(Component::getItemId)
            .collect(Collectors.toSet())

        for (id in itemIds) {
            if (!depositAll(script, ComponentQuery.newQuery(517).itemId(id))) return false
        }
        return itemIds.isNotEmpty()
    }

    /** Suspendable variant: deposit everything except ids. */
    suspend fun depositAllExcept(script: SuspendableScript, vararg ids: Int): Boolean {
        val idSet = Arrays.stream(ids).boxed().collect(Collectors.toSet())
        val itemIds = ComponentQuery.newQuery(517).results().stream()
            .filter { i -> !idSet.contains(i.itemId) && (i.options.contains("Deposit-All") || i.options.contains("Deposit-1")) }
            .map(Component::getItemId)
            .collect(Collectors.toSet())
        for (id in itemIds) {
            if (!depositAll(script, ComponentQuery.newQuery(517).itemId(id))) return false
        }
        return itemIds.isNotEmpty()
    }

    /** Suspendable variant: deposit everything except regex patterns. */
    suspend fun depositAllExcept(script: SuspendableScript, vararg patterns: Pattern): Boolean {
        val idMap = Backpack.getItems().stream()
            .filter { i: Item -> i.name != null && Arrays.stream(patterns).anyMatch { p: Pattern -> p.matcher(i.name).matches() } }
            .collect(Collectors.toMap(Item::getId, Item::getName))
        val itemIds = ComponentQuery.newQuery(517).results().stream()
            .filter { i -> !idMap.containsKey(i.itemId) && (i.options.contains("Deposit-All") || i.options.contains("Deposit-1")) }
            .map(Component::getItemId)
            .collect(Collectors.toSet())
        for (id in itemIds) {
            if (!depositAll(script, ComponentQuery.newQuery(517).itemId(id))) return false
        }
        return itemIds.isNotEmpty()
    }

    suspend fun depositEquipment(script: SuspendableScript): Boolean {
        val res = depositEquipment()
        if (res) script.awaitTicks(1)
        return res
    }

    suspend fun depositBackpack(script: SuspendableScript): Boolean {
        val res = depositBackpack()
        if (res) script.awaitTicks(1)
        return res
    }

    suspend fun withdrawAll(script: SuspendableScript, name: String): Boolean {
        val res = withdrawAll(name)
        if (res) script.awaitTicks(1)
        return res
    }

    suspend fun withdrawAll(script: SuspendableScript, id: Int): Boolean {
        val res = withdrawAll(id)
        if (res) script.awaitTicks(1)
        return res
    }

}

enum class TransferOptionType(val varbitStateValue: Int) {
    ONE(2),
    FIVE(3),
    TEN(4),
    ALL(7),
    X(5)
}
