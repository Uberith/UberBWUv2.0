package com.uberith.api.game.skills.woodcutting

import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.xapi.query.SceneObjectQuery
import java.util.regex.Pattern

object Trees {

    /**
     * Stateless woodcutting interaction request used by the fluent [TreeChopRequestBuilder].
     */
    class TreeChopRequest internal constructor(
        private val locator: () -> SceneObject?,
        private val interactor: (SceneObject) -> Boolean
    ) {
        /** Attempts to locate the nearest matching tree and interact with it. */
        fun nearest(): Boolean = locator()?.let(interactor) ?: false

        /** Returns the nearest matching tree without interacting. */
        fun nearestObject(): SceneObject? = locator()

        /** Interacts with the provided [target] if it is non-null. */
        fun target(target: SceneObject?): Boolean = target?.let(interactor) ?: false
    }

    /**
     * Fluent builder used by [Trees.chop] to configure tree selection before issuing interactions.
     * Example: `Trees.chop().name("Maple").nearest()`
     */
    class TreeChopRequestBuilder internal constructor() {

        private sealed interface Lookup {
            data object Any : Lookup
            data class Type(val type: TreeType) : Lookup
            data class Exact(val pattern: Pattern) : Lookup
        }

        private var lookup: Lookup = Lookup.Any

        fun any(): TreeChopRequestBuilder = apply { lookup = Lookup.Any }

        fun type(type: TreeType): TreeChopRequestBuilder = apply { lookup = Lookup.Type(type) }

        fun name(name: String): TreeChopRequestBuilder = apply {
            val trimmed = name.trim()
            lookup = resolveTreeType(trimmed)?.let(Lookup::Type)
                ?: Lookup.Exact(exactNamePattern(trimmed))
        }

        fun pattern(pattern: Pattern): TreeChopRequestBuilder = apply {
            lookup = Lookup.Exact(pattern)
        }

        fun nearest(): Boolean = locate()?.let(this@Trees::chop) ?: false

        fun nearestObject(): SceneObject? = locate()

        fun target(target: SceneObject?): Boolean = target?.let(this@Trees::chop) ?: false

        fun build(): TreeChopRequest = TreeChopRequest(
            locator = ::locate,
            interactor = this@Trees::chop
        )

        private fun locate(): SceneObject? = when (val spec = lookup) {
            Lookup.Any -> this@Trees.nearest()
            is Lookup.Type -> this@Trees.nearest(spec.type)
            is Lookup.Exact -> this@Trees.nearest(spec.pattern)
        }

        private fun exactNamePattern(name: String): Pattern =
            Pattern.compile("^" + Pattern.quote(name) + "$", Pattern.CASE_INSENSITIVE)
    }

    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Trees::class.java)

    /**
     * Starts a fluent woodcutting request builder.
     */
    fun chop(): TreeChopRequestBuilder = TreeChopRequestBuilder()

    private val treeTypes = TreeType.values()

    fun resolveTreeType(name: String): TreeType? {
        val norm = toTitleCase(name.trim())
        return treeTypes.firstOrNull { t ->
            val disp = t.displayName
            val short = disp.removeSuffix(" tree").removeSuffix(" Tree")
            norm.equals(disp, ignoreCase = true) || norm.equals(short, ignoreCase = true)
        }
    }

    // Common preferred interaction actions for trees (include Ivy's "Chop")
    private val preferredActions = listOf("Chop down", "Cut down", "Chop")
    private val preferredActionsArray = preferredActions.toTypedArray()

    private fun chooseAction(options: List<String?>): String =
        preferredActions.firstOrNull { pref ->
            options.any { option -> option?.equals(pref, ignoreCase = true) == true }
        } ?: preferredActions.first()

    private fun interact(obj: SceneObject): Boolean {
        val options = obj.options ?: emptyList()
        val action = chooseAction(options)
        logger.info("[Trees] interact: name='{}', typeId={}, action='{}', options={}", obj.name, obj.typeId, action, options)
        val res = obj.interact(action)
        val ok = res > 0
        if (!ok) logger.warn("[Trees] interact failed: name='{}', typeId={}, action='{}'", obj.name, obj.typeId, action)
        return ok
    }

    private fun treeQuery() = SceneObjectQuery.newQuery().hidden(false)

    /**
     * Finds the nearest visible scene object matching [type].
     *
     * @param type tree type to locate
     * @return nearest visible [SceneObject] or null if none
     */
    fun nearest(type: TreeType): SceneObject? {
        logger.info("[Trees] nearest(type='{}'): begin", type.displayName)
        val obj: SceneObject = treeQuery()
            .name(type.namePattern)
            .option(*preferredActionsArray)
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

    fun nearest(): SceneObject? {
        logger.info("[Trees] nearest(): begin (any tree)")
        val obj = treeQuery()
            .option(*preferredActionsArray)
            .results()
            .nearest() ?: run {
                logger.info("[Trees] nearest(): none found")
                return null
            }
        logger.info("[Trees] nearest(): name='{}', typeId={}, options={}", obj.name, obj.typeId, obj.options)
        return obj
    }

    fun nearest(pattern: Pattern): SceneObject? {
        logger.info("[Trees] nearest(pattern='{}'): begin", pattern.pattern())
        val obj = treeQuery()
            .name(pattern)
            .results()
            .firstOrNull() ?: run {
                logger.info("[Trees] nearest(pattern='{}'): none found", pattern.pattern())
                return null
            }
        logger.info("[Trees] nearest(pattern='{}'): name='{}', typeId={}, options={}", pattern.pattern(), obj.name, obj.typeId, obj.options)
        return obj
    }

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
        logger.info("[Trees] chop(type='{}'): builder request created", type.displayName)
        return chop().type(type).build()
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
        logger.info("[Trees] chop(name='{}'): builder request created", trimmed)
        return chop().name(trimmed).build()
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

    private suspend fun SuspendableScript.awaitPostChop(success: Boolean): Boolean {
        if (success) awaitTicks(2)
        return success
    }

    /**
     * Suspends then chops the nearest tree matching [type].
     *
     * @param script coroutine context with await utilities
     * @param type tree type to chop
     * @return true if the interaction was issued
     */
    suspend fun chop(script: SuspendableScript, type: TreeType): Boolean {
        return script.awaitPostChop(chop(type).nearest())
    }

    /**
     * Suspends then chops a specific [target] previously obtained from [nearest].
     *
     * @param script coroutine context with await utilities
     * @param target scene object representing the tree
     * @return true if the interaction was issued
     */
    suspend fun chop(script: SuspendableScript, target: SceneObject): Boolean {
        return script.awaitPostChop(chop(target))
    }

    /**
     * Suspends then chops by human‑friendly [name].
     *
     * @param script coroutine context with await utilities
     * @param name tree name
     * @return true if the interaction was issued
     */
    suspend fun chop(script: SuspendableScript, name: String): Boolean {
        return script.awaitPostChop(chop(name).nearest())
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
        treeTypes.filter { level >= it.levelReq && (!it.membersOnly || isMember) }

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
        val obj = treeQuery()
            .name(contains, name.trim())
            .option(contains, "chop")
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
    fun allTypes(): List<TreeType> = treeTypes.toList()

    /**
     * Returns a list of all tree display names formatted in Title Case
     * (aka space-separated Camel Case) suitable for GUI presentation.
     *
     * Examples:
     * - "Magic tree" -> "Magic Tree"
     * - "Eternal magic tree" -> "Eternal Magic Tree"
     */
    fun allNamesCamelCase(): List<String> =
        treeTypes.map { toTitleCase(it.displayName) }

    /**
     * Returns pairs of ([TreeType], Title-Case name) for convenient binding in UIs.
     */
    fun allWithCamelCaseNames(): List<Pair<TreeType, String>> =
        treeTypes.map { it to toTitleCase(it.displayName) }

    private fun toTitleCase(text: String): String =
        text.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch -> ch.titlecase() }
            }
}
