package com.uberith.api.game.skills.woodcutting

import com.uberith.api.SuspendableScript
import com.uberith.api.game.world.internal.SceneObjectRef
import com.uberith.api.game.world.SceneObjectQuery
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
 * - Query layer: Uses a pluggable provider via [SceneObjects.provider] to locate scene objects by
 *   name and visibility, avoiding any direct dependency on a specific client/XAPI.
 * - Decoupling: The provider returns a lightweight [SceneObjectRef] with only the fields and
 *   methods we need (`id`, `options`, `interact`). Host modules can implement this interface using
 *   their preferred client APIs (with or without reflection).
 * - Safety: This module performs no reflection; any reflective access (if needed) is isolated to
 *   the host's provider implementation.
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
object Trees {

    /**
     * Finds the nearest visible scene object matching [type].
     *
     * Matching details
     * - Uses [TreeType.namePattern] (case‑insensitive) to match by display name.
     * - Excludes known invalid variants when an `id` field is present and listed in
     *   [TreeType.excludedIds]. If the `id` field is absent or unreadable, no id‑based filtering
     *   is applied.
     *
     * Returns
     * - The underlying platform object (opaque `Any`) or `null` if none found.
     *
     * Limitations
     * - No pathfinding or reachability checks are performed.
     * - Requires the object to be visible (`hidden(false)`).
     */
    fun nearest(type: TreeType): Any? {
        val obj = SceneObjectQuery
            .newQuery()
            .name(type.namePattern)
            .hidden(false)
            .results()
            .firstOrNull() ?: return null
        val ref = obj as? SceneObjectRef ?: return obj
        return if (ref.id == null || !type.excludedIds.contains(ref.id!!)) obj else null
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
    fun chop(type: TreeType): Boolean {
        val obj = nearest(type) as? SceneObjectRef ?: return false
        // Preferred options
        val preferred = listOf("Chop down", "Cut down")
        val options = obj.options
        val action = options.firstOrNull { it != null && preferred.any { p -> p.equals(it, ignoreCase = true) } }
            ?: preferred.first()
        val res = obj.interact(action)
        return res > 0
    }

    /**
     * Counts visible scene objects that match [type].
     *
     * Notes
     * - Intended for lightweight presence checks and UI gating; does not favor proximity and
     *   does not imply reachability.
     */
    fun count(type: TreeType): Int =
        SceneObjectQuery.newQuery().name(type.namePattern).hidden(false).results().size

    /**
     * Suspendable variant of [chop]: attempts interaction and yields for ~2 game ticks on success.
     *
     * Intended use
     * - For coroutine‑driven scripts ([SuspendableScript]) to pace interactions without busy waiting.
     *
     * Returns
     * - `true` if the interaction was initiated; `false` otherwise.
     */
    suspend fun chop(script: SuspendableScript, type: TreeType): Boolean {
        val ok = chop(type)
        if (ok) script.awaitTicks(2)
        return ok
    }

    /**
     * Builds a case‑insensitive alternation [Pattern] for multiple exact tree names.
     *
     * Example
     * ```kotlin
     * val p = Trees.patternFor("Oak", "Willow", "Yew")
     * // Produces a regex that matches any of those names, case‑insensitively
     * ```
     */
    fun patternFor(vararg names: String): Pattern =
        Pattern.compile(names.joinToString("|") { Pattern.quote(it) }, Pattern.CASE_INSENSITIVE)

    /**
     * Lists all [TreeType] entries available for a Woodcutting [level] and membership flag.
     *
     * Rules
     * - Includes types where `level >= levelReq`.
     * - Excludes member‑only types when [isMember] is `false`.
     */
    fun availableFor(level: Int, isMember: Boolean): List<TreeType> =
        TreeType.values().filter { level >= it.levelReq && (!it.membersOnly || isMember) }

    /**
     * Returns the highest‑requirement [TreeType] available for the given [level] and [isMember].
     * Returns `null` if no types qualify.
     */
    fun bestFor(level: Int, isMember: Boolean): TreeType? =
        availableFor(level, isMember).maxByOrNull { it.levelReq }
}
