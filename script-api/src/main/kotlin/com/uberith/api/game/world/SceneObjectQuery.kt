package com.uberith.api.game.world

import com.uberith.api.game.world.internal.SceneObjectProvider
import com.uberith.api.game.world.internal.SceneObjectRef
import com.uberith.api.game.world.internal.SceneObjects
import java.util.Arrays
import java.util.regex.Pattern

/**
 * Fluent query builder for scene objects, modeled after the external XAPI
 * `SceneObjectQuery`, but without taking a hard dependency on that library.
 *
 * Key ideas
 * - Pluggable source: queries evaluate over objects supplied by the internal
 *   provider (`internal.SceneObjects.provider.all()`). Host code installs the
 *   provider at startup; this module stays client‑agnostic.
 * - Predicate chaining: each call (e.g., [typeId], [name], [option], [hidden])
 *   adds another filter to a predicate that is applied when [results] is called.
 * - Opaque results: the public API returns `Any` to avoid exposing internal
 *   support types. In practice, values are instances of
 *   `internal.SceneObjectRef` and can be safely down‑cast within this module.
 *
 * Ordering and performance
 * - [results] materializes a `List<Any>` in the order supplied by the provider.
 *   If your provider returns nearest‑first, then `results().firstOrNull()` is
 *   the nearest object by that definition.
 * - All filters are evaluated in‑memory after the provider enumerates objects;
 *   for very large worlds, prefer using selective providers or pre‑filters.
 *
 * Usage
 * ```kotlin
 * // Find the nearest visible Yew tree by exact name
 * val nearest = SceneObjectQuery
 *     .newQuery()
 *     .name(java.util.regex.Pattern.compile("^Yew$", java.util.regex.Pattern.CASE_INSENSITIVE))
 *     .hidden(false)
 *     .results()
 *     .firstOrNull()
 *
 * // Filter by multiple options (case‑sensitive contentEquals in this overload)
 * val withChop = SceneObjectQuery.newQuery().option("Chop down", "Cut down").results()
 * ```
 */
class SceneObjectQuery : Iterable<Any> {

    private var root: (SceneObjectRef) -> Boolean = { true }

    companion object {
        /** Creates a fresh query with no filters applied. */
        fun newQuery(): SceneObjectQuery = SceneObjectQuery()

        init {
            // Attempt to auto-install a default provider backed by RS3 World if available.
            tryInstallDefaultProvider()
        }

        private fun tryInstallDefaultProvider() {
            try {
                val worldCls = Class.forName("net.botwithus.rs3.world.World")
                val getSceneObjects = worldCls.getMethod("getSceneObjects")

                class WorldBackedRef(private val delegate: Any) : SceneObjectRef {
                    override val id: Int? = try {
                        // Prefer typeId as a stable identifier for filtering
                        val m = delegate.javaClass.getMethod("getTypeId")
                        (m.invoke(delegate) as? Number)?.toInt()
                    } catch (_: Throwable) { null }

                    override val typeId: Int? = id

                    override val animationId: Int? = try {
                        val m = delegate.javaClass.getMethod("getAnimationId")
                        (m.invoke(delegate) as? Number)?.toInt()
                    } catch (_: Throwable) { null }

                    override val name: String? = try {
                        val m = delegate.javaClass.getMethod("getName")
                        m.invoke(delegate) as? String
                    } catch (_: Throwable) { null }

                    override val hidden: Boolean = try {
                        val m = delegate.javaClass.getMethod("isHidden")
                        (m.invoke(delegate) as? Boolean) == true
                    } catch (_: Throwable) { false }

                    override val options: List<String?> = try {
                        val m = delegate.javaClass.getMethod("getOptions")
                        (m.invoke(delegate) as? List<*>)?.map { it as? String } ?: emptyList()
                    } catch (_: Throwable) { emptyList() }

                    override val multiType: Any? = try {
                        val m = delegate.javaClass.methods.firstOrNull { it.name.contains("MultiType", ignoreCase = true) && it.parameterCount == 0 }
                        m?.invoke(delegate)
                    } catch (_: Throwable) { null }

                    override fun interact(action: String): Int = try {
                        val m = delegate.javaClass.getMethod("interact", String::class.java)
                        (m.invoke(delegate, action) as? Number)?.toInt() ?: 0
                    } catch (_: Throwable) { 0 }
                }

                val provider = object : SceneObjectProvider {
                    override fun all(): Sequence<SceneObjectRef> {
                        val col = getSceneObjects.invoke(null) as? java.util.Collection<*> ?: return emptySequence()
                        return col.asSequence().mapNotNull { it?.let { WorldBackedRef(it) } }
                    }
                }

                SceneObjects.install(provider)
            } catch (_: Throwable) {
                // Ignore; host can install its own provider.
            }
        }
    }

    /**
     * Evaluates the current predicate over the provider's objects and returns
     * a materialized list. The element type is intentionally `Any` to prevent
     * leaking internal support types; consumers typically pass the values back
     * into higher‑level APIs rather than inspecting them directly.
     */
    fun results(): List<Any> {
        val list = SceneObjects.provider
            .all()
            .filter { ref -> root(ref) }
            .map { it as Any }
            .toList()
        return list
    }

    override fun iterator(): Iterator<Any> = results().iterator()

    /**
     * Tests a single object against the current predicate.
     * Accepts `Any` to avoid exposing internal types; returns false for
     * unsupported instances.
     */
    fun test(sceneObject: Any): Boolean =
        (sceneObject as? SceneObjectRef)?.let(root) ?: false

    /**
     * Filters by one or more object type identifiers.
     * No‑op when [typeIds] is empty.
     */
    fun typeId(vararg typeIds: Int): SceneObjectQuery {
        if (typeIds.isEmpty()) return this
        val set = typeIds.toSet()
        root = { t -> root(t) && t.typeId != null && set.contains(t.typeId) }
        return this
    }

    /**
     * Filters by one or more animation identifiers (exact matches).
     * No‑op when [animations] is empty.
     */
    fun animation(vararg animations: Int): SceneObjectQuery {
        if (animations.isEmpty()) return this
        val set = animations.toSet()
        root = { t -> root(t) && t.animationId != null && set.contains(t.animationId) }
        return this
    }

    /**
     * Filters by visibility state as reported by the provider.
     * Use `hidden(false)` for visible objects only.
     */
    fun hidden(hidden: Boolean): SceneObjectQuery {
        root = { t -> root(t) && t.hidden == hidden }
        return this
    }

    /**
     * Filters by one or more multi‑type descriptors. Descriptors are treated as
     * opaque values supplied by the provider; equality uses `Set.contains`.
     * No‑op when [sceneObjectDefinitions] is empty.
     */
    fun multiType(vararg sceneObjectDefinitions: Any): SceneObjectQuery {
        if (sceneObjectDefinitions.isEmpty()) return this
        val defs = sceneObjectDefinitions.toSet()
        root = { t -> root(t) && t.multiType != null && defs.contains(t.multiType) }
        return this
    }

    /**
     * Filters by name using a custom two‑argument predicate (e.g.,
     * `String::equals` or case‑insensitive checks). No‑op when [names] is empty.
     */
    fun name(spred: (String, CharSequence) -> Boolean, vararg names: String): SceneObjectQuery {
        if (names.isEmpty()) return this
        root = { t ->
            val objName = t.name
            root(t) && objName != null && names.any { n -> spred(n, objName) }
        }
        return this
    }

    /**
     * Filters by exact name matches using `String.contentEquals` semantics.
     */
    fun name(vararg names: String): SceneObjectQuery = name(String::contentEquals, *names)

    /**
     * Filters by name against one or more regular expression [patterns].
     * Patterns are matched using `Pattern.matcher(name).matches()` (full match).
     * No‑op when [patterns] is empty.
     */
    fun name(vararg patterns: Pattern): SceneObjectQuery {
        if (patterns.isEmpty()) return this
        root = { t ->
            val objName = t.name
            root(t) && objName != null && Arrays.stream(patterns).anyMatch { p -> p.matcher(objName).matches() }
        }
        return this
    }

    /**
     * Filters by available action options using a custom predicate
     * (e.g., `String::contentEquals` or case‑insensitive checks). This matches
     * when any provided [options] satisfies the predicate for any non‑null
     * option exposed by the object. No‑op when [options] is empty.
     */
    fun option(spred: (String, CharSequence) -> Boolean, vararg options: String): SceneObjectQuery {
        if (options.isEmpty()) return this
        root = { t ->
            val objOptions: List<String?> = t.options
            root(t) && objOptions.isNotEmpty() && options.any { i ->
                objOptions.any { j -> j != null && spred(i, j as CharSequence) }
            }
        }
        return this
    }

    /** Filters by options using `String.contentEquals` semantics. */
    fun option(vararg option: String): SceneObjectQuery = option(String::contentEquals, *option)

    /**
     * Filters by options using one or more regular expressions.
     * Each option is tested with `Pattern.matcher(option).matches()`.
     * No‑op when [patterns] is empty.
     */
    fun option(vararg patterns: Pattern): SceneObjectQuery {
        if (patterns.isEmpty()) return this
        root = { t ->
            val objOptions: List<String?> = t.options
            root(t) && objOptions.isNotEmpty() && objOptions.any { opt ->
                opt != null && Arrays.stream(patterns).anyMatch { p -> p.matcher(opt).matches() }
            }
        }
        return this
    }
}
