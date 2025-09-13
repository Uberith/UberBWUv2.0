package com.uberith.api.game.skills.woodcutting

import com.uberith.api.SuspendableScript
import com.uberith.api.nearest
import com.uberith.api.game.query.SceneObjectQuery
import net.botwithus.rs3.entities.SceneObject
import java.util.regex.Pattern


/**
 * Trees API — convenience helpers for querying and interacting with in‑world trees.
 *
 * What this provides
 * - High‑level helpers: [nearest], [chop], [count] keep call‑sites small and readable.
 * - Centralized metadata: naming patterns, level requirements, and membership constraints live in [TreeType].
 * - Coroutine support: a suspendable [chop] overload integrates neatly with [SuspendableScript].
 *
 * Architectural notes
 * - Query layer: Uses SceneObjectQuery to locate scene objects by
 *   name and visibility, avoiding direct dependencies at call sites.
 * - Decoupling: We interact with net.botwithus.rs3.entities.SceneObject directly.
 *
 * Behavior and assumptions
 * - Name matching: [TreeType.namePattern] is a case‑insensitive regex crafted for exact tree names
 *   (e.g., "^Yew$"); this avoids accidental matches on similarly named objects.
 * - Action preference: [chop] prefers "Chop down", then falls back to "Cut down" if available.
 * - Exclusions: Some trees share models with unchoppable scenery; those object ids are excluded
 *   via [TreeType.excludedIds] when available.
 * - Distance/visibility: [nearest] and [count] operate on visible scene objects; they do not pathfind
 *   or verify reachability.
 *
 * Performance
 * - Queries are executed per call. For rapid polling, consider caching the last result or throttling
 *   invocations to avoid excessive query pressure.
 *
 * Threading
 * - Methods are stateless and thread‑safe under the assumption that the underlying query engine is
 *   itself thread‑safe for read operations.
 *
 * Usage examples
 * ```kotlin
 * // Find the closest yew and try to chop it
 * val found = Trees.chop(TreeType.YEW)
 *
 * // Suspendable usage inside a SuspendableScript
 * val ok = Trees.chop(this, TreeType.MAGIC) // yields ~2 ticks on success
 *
 * // Choose best available by level/membership
 * val best = Trees.bestFor(level = 61, isMember = true)
 * best?.let { Trees.chop(it) }
 * ```
 */
// Wrapper no longer needed; APIs now use SceneObject directly.

object Trees {

    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Trees::class.java)

    // Common preferred interaction actions for trees
    private val preferredActions = listOf("Chop down", "Cut down")

    private fun chooseAction(options: List<String?>): String =
        options.firstOrNull { it != null && preferredActions.any { p -> p.equals(it, ignoreCase = true) } }
            ?: preferredActions.first()

    private fun interact(obj: SceneObject): Boolean {
        val options = obj.options ?: emptyList()
        val action = chooseAction(options)
        logger.info("[Trees] interact: name='{}', typeId={}, action='{}', options={}", obj.name, obj.typeId, action, options)
        val res = obj.interact(action)
        val ok = res > 0
        if (!ok) logger.warn("[Trees] interact failed: name='{}', typeId={}, action='{}'", obj.name, obj.typeId, action)
        return ok
    }

    /**
     * Finds the nearest visible scene object matching [type].
     *
     * @param type tree type to locate
     * @return nearest visible [SceneObject] or null if none
     */
    fun nearest(type: TreeType): SceneObject? {
        logger.info("[Trees] nearest(type='{}'): begin", type.displayName)
        val obj: SceneObject = SceneObjectQuery
            .newQuery()
            .name(type.namePattern)
            .option("Chop down", "Cut down")
            .hidden(false)
            .results()
            .nearest() ?: run {
                logger.info("[Trees] nearest(type='{}'): none found", type.displayName)
                return null
            }
        // Exclude specific object type ids if configured
        if (type.excludedIds.contains(obj.typeId)) {
            logger.info("[Trees] nearest(type='{}'): excluded by typeId {}", type.displayName, obj.typeId)
            return null
        }
        logger.info("[Trees] nearest(type='{}'): name='{}', typeId={}, options={}", type.displayName, obj.name, obj.typeId, obj.options)
        return obj
    }

    /**
     * Attempts to interact with the nearest matching tree using a preferred action.
     *
     * Action resolution
     * - Preferred actions (in order): "Chop down" → "Cut down".
     * - If neither action is present in the object options, we still attempt the first preferred
     *   action name (as some platforms allow sending an action not enumerated in options).
     *
     * Returns
     * - `true` if an interaction was sent (method returned a positive code), otherwise `false`.
     *
     * Caveats
     * - This method does not wait for an animation or success state; use the suspendable overload
     *   or handle waiting externally.
     */
    /**
     * Chops the nearest tree matching [type] using a preferred action.
     *
     * @param type tree type to chop
     * @return true if the interaction was issued
     */
    fun chop(type: TreeType): Boolean {
        logger.info("[Trees] chop(type='{}'): begin", type.displayName)
        val obj = nearest(type)
        return obj?.let {
            logger.info("[Trees] chop(type='{}'): target name='{}', typeId={}", type.displayName, it.name, it.typeId)
            interact(it)
        } ?: run {
            logger.info("[Trees] chop(type='{}'): no target", type.displayName)
            false
        }
    }

    /**
     * Chops a specific tree object previously obtained from [nearest].
     *
     * @param target scene object representing the tree
     * @return true if the interaction was issued
     */
    fun chop(target: SceneObject): Boolean {
        logger.info("[Trees] chop(target): name='{}', typeId={}", target.name, target.typeId)
        return interact(target)
    }

    /**
     * Chops by human‑friendly tree [name] (e.g., "Oak", "Oak Tree").
     * Attempts to resolve to a [TreeType] first; otherwise falls back to direct name match.
     *
     * @param name tree name
     * @return true if the interaction was issued
     */
    fun chop(name: String): Boolean {
        val norm = toTitleCase(name.trim())
        val type = TreeType.values().firstOrNull { t ->
            val disp = t.displayName
            val short = disp.removeSuffix(" tree").removeSuffix(" Tree")
            norm.equals(disp, ignoreCase = true) || norm.equals(short, ignoreCase = true)
        }
        if (type != null) return chop(type)

        val pattern = Pattern.compile("^" + Pattern.quote(name.trim()) + "$", Pattern.CASE_INSENSITIVE)
        logger.info("Chop by exact name: '{}'", name)
        val obj = SceneObjectQuery
            .newQuery()
            .name(pattern)
            .hidden(false)
            .results()
            .firstOrNull() ?: return false
        logger.info("Exact name found: name='{}', typeId={}", obj.name, obj.typeId)
        return interact(obj)
    }

    /**
     * Counts visible scene objects that match [type].
     *
     * Notes
     * - Intended for lightweight presence checks and UI gating; does not favor proximity and
     *   does not imply reachability.
     */
    fun count(type: TreeType): Int {
        val size = SceneObjectQuery.newQuery().name(type.namePattern).hidden(false).results().size()
        logger.info("Count for '{}': {}", type.displayName, size)
        return size
    }

    /**
     * Suspends then chops the nearest tree matching [type].
     *
     * @param script coroutine context with await utilities
     * @param type tree type to chop
     * @return true if the interaction was issued
     */
    suspend fun chop(script: SuspendableScript, type: TreeType): Boolean {
        val ok = chop(type)
        if (ok) script.awaitTicks(2)
        return ok
    }

    /**
     * Suspends then chops a specific [target] previously obtained from [nearest].
     *
     * @param script coroutine context with await utilities
     * @param target scene object representing the tree
     * @return true if the interaction was issued
     */
    suspend fun chop(script: SuspendableScript, target: SceneObject): Boolean {
        val ok = chop(target)
        if (ok) script.awaitTicks(2)
        return ok
    }

    /**
     * Suspends then chops by human‑friendly [name].
     *
     * @param script coroutine context with await utilities
     * @param name tree name
     * @return true if the interaction was issued
     */
    suspend fun chop(script: SuspendableScript, name: String): Boolean {
        val ok = chop(name)
        if (ok) script.awaitTicks(2)
        return ok
    }

    /**
     * Builds a case‑insensitive alternation regex for multiple exact names.
     *
     * @param names exact names to match
     * @return compiled [Pattern] matching any provided name
     */
    fun patternFor(vararg names: String): Pattern =
        Pattern.compile(names.joinToString("|") { Pattern.quote(it) }, Pattern.CASE_INSENSITIVE)

    /**
     * Lists [TreeType] entries available for a Woodcutting [level] and membership flag.
     *
     * @param level woodcutting level
     * @param isMember membership flag
     * @return list of available tree types
     */
    fun availableFor(level: Int, isMember: Boolean): List<TreeType> =
        TreeType.values().filter { level >= it.levelReq && (!it.membersOnly || isMember) }

    /**
     * Picks the highest‑requirement available [TreeType] for the given context.
     *
     * @param level woodcutting level
     * @param isMember membership flag
     * @return best available type or null if none
     */
    fun bestFor(level: Int, isMember: Boolean): TreeType? =
        availableFor(level, isMember).maxByOrNull { it.levelReq }

    /**
     * Finds the nearest tree by human‑friendly [name] (e.g., "Maple", "Maple Tree").
     * Resolves to a [TreeType] first, otherwise falls back to direct name matching.
     *
     * @param name tree name
     * @return nearest visible [SceneObject] or null if none
     */
    fun nearest(name: String): SceneObject? {
        val norm = toTitleCase(name.trim())
        logger.info("[Trees] nearest(name): raw='{}', normalized='{}'", name, norm)
        val type = TreeType.values().firstOrNull { t ->
            val disp = t.displayName
            val short = disp.removeSuffix(" tree").removeSuffix(" Tree")
            norm.equals(disp, ignoreCase = true) || norm.equals(short, ignoreCase = true)
        }
        if (type != null) {
            logger.info("[Trees] nearest(name): resolved to type='{}'", type.displayName)
            return nearest(type)
        }

        // Fallback: relaxed matching - contains(name, ignoreCase) and has a chop option
        val contains: (String, CharSequence) -> Boolean = { needle, hay ->
            hay.toString().contains(needle, ignoreCase = true)
        }
        logger.info("[Trees] nearest(name): fallback contains(name) + has chop option")
        val obj = SceneObjectQuery
            .newQuery()
            .name(contains, name.trim())
            .option(contains, "chop")
            .hidden(false)
            .results()
            .firstOrNull() ?: run {
                logger.info("[Trees] nearest(name): no fallback match for '{}'", name)
                return null
            }
        logger.info("[Trees] nearest(name): fallback name='{}', typeId={}", obj.name, obj.typeId)
        return obj
    }

    /**
     * Returns all supported [TreeType] values in their declared enum order.
     * Useful for building full selection lists in UIs.
     */
    fun allTypes(): List<TreeType> = TreeType.values().toList()

    /**
     * Returns a list of all tree display names formatted in Title Case
     * (aka space-separated Camel Case) suitable for GUI presentation.
     *
     * Examples:
     * - "Magic tree" -> "Magic Tree"
     * - "Eternal magic tree" -> "Eternal Magic Tree"
     */
    fun allNamesCamelCase(): List<String> =
        allTypes().map { toTitleCase(it.displayName) }

    /**
     * Returns pairs of ([TreeType], Title-Case name) for convenient binding in UIs.
     */
    fun allWithCamelCaseNames(): List<Pair<TreeType, String>> =
        allTypes().map { it to toTitleCase(it.displayName) }

    private fun toTitleCase(text: String): String =
        text.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch -> ch.titlecase() }
            }
}
