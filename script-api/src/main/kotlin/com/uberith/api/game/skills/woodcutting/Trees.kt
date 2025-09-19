package com.uberith.api.game.skills.woodcutting

import net.botwithus.kxapi.game.query.SceneObjectQuery
import net.botwithus.kxapi.game.query.result.nearest
import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.entities.SceneObject
import java.util.regex.Pattern


class TreeChopRequest internal constructor(
    private val locator: () -> SceneObject?,
    private val interactor: (SceneObject) -> Boolean
) {
    /**
     * Attempts to locate the nearest matching tree and interact with it.
     *
     * @return true if an interaction was issued, false if nothing matched
     */
    fun nearest(): Boolean {
        val target = locator() ?: return false
        return interactor(target)
    }

    /**
     * Returns the nearest matching tree without interacting.
     */
    fun nearestObject(): SceneObject? = locator()

    /**
     * Interacts with the provided [target] if it is non-null.
     */
    fun target(target: SceneObject?): Boolean = target?.let(interactor) ?: false
}
object Trees {

    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Trees::class.java)

    fun resolveTreeType(name: String): TreeType? {
        val norm = toTitleCase(name.trim())
        return TreeType.values().firstOrNull { t ->
            val disp = t.displayName
            val short = disp.removeSuffix(" tree").removeSuffix(" Tree")
            norm.equals(disp, ignoreCase = true) || norm.equals(short, ignoreCase = true)
        }
    }

    // Common preferred interaction actions for trees (include Ivy's "Chop")
    private val preferredActions = listOf("Chop down", "Cut down", "Chop")

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
     * Builds a [TreeChopRequest] configured for the supplied [type].
     *
     * Call [TreeChopRequest.nearest] to locate and chop the closest matching tree,
     * or [TreeChopRequest.nearestObject] if you only need the entity reference.
     *
     * @param type tree type to chop
     * @return request builder for chaining (e.g., .nearest())
     */
    fun chop(type: TreeType): TreeChopRequest {
        val descriptor = "type='${'$'}{type.displayName}'"
        return TreeChopRequest(
            locator = {
                logger.info("[Trees] chop({}): begin", descriptor)
                val obj = nearest(type)
                if (obj == null) {
                    logger.info("[Trees] chop({}): no target", descriptor)
                } else {
                    logger.info("[Trees] chop({}): target name='{}', typeId={}", descriptor, obj.name, obj.typeId)
                }
                obj
            },
            interactor = { target -> interact(target) }
        )
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
     * Builds a [TreeChopRequest] that targets trees identified by [name] (e.g., "Oak").
     * Attempts to resolve to a [TreeType] first; otherwise falls back to exact-name matching.
     *
     * @param name tree name
     * @return request builder for chaining (e.g., .nearest())
     */
    fun chop(name: String): TreeChopRequest {
        val trimmed = name.trim()
        val type = resolveTreeType(trimmed)
        if (type != null) return chop(type)

        return TreeChopRequest(
            locator = {
                val pattern = Pattern.compile("^" + Pattern.quote(trimmed) + "$", Pattern.CASE_INSENSITIVE)
                logger.info("Chop by exact name: '{}'", trimmed)
                SceneObjectQuery
                    .newQuery()
                    .name(pattern)
                    .hidden(false)
                    .results()
                    .firstOrNull()
                    ?.also { logger.info("Exact name found: name='{}', typeId={}", it.name, it.typeId) }
            },
            interactor = { target -> interact(target) }
        )
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
        val ok = chop(type).nearest()
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
        val ok = chop(name).nearest()
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
        val type = resolveTreeType(norm)
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

    val locations: List<TreeLocation>
        get() = TreeLocations.ALL

    fun locationsFor(type: TreeType): List<TreeLocation> = TreeLocations.locationsFor(type)

    fun locationsFor(name: String): List<TreeLocation> = TreeLocations.locationsFor(name)

    fun resolveLocation(treeName: String, savedLocation: String?): TreeLocation? {
        val candidates = locationsFor(treeName)
        if (candidates.isEmpty()) return null
        if (!savedLocation.isNullOrBlank()) {
            candidates.firstOrNull { it.name.equals(savedLocation, ignoreCase = true) }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    private fun toTitleCase(text: String): String =
        text.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch -> ch.titlecase() }
            }
}
