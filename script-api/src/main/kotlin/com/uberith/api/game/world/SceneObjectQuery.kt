package com.uberith.api.game.world

import net.botwithus.rs3.cache.assets.so.SceneObjectDefinition
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.world.World
import net.botwithus.rs3.world.Distance
import java.util.Arrays
import java.util.regex.Pattern

/**
 * Kotlin query builder for RS3 SceneObjects, aligned with the XAPI Java variant.
 *
 * - Backed directly by `World.getSceneObjects()`.
 * - Chained filters (typeId, animation, hidden, multiType, name, option).
 * - `results()` materializes a list of matching `SceneObject`s.
 */
class SceneObjectQuery : Iterable<SceneObject> {

    private var root: (SceneObject) -> Boolean = { true }

    companion object {
        fun newQuery(): SceneObjectQuery = SceneObjectQuery()
    }

    /** Returns the filtered results as a List in the World iteration order. */
    fun results(): List<SceneObject> =
        World.getSceneObjects()
            .asSequence()
            .filter { so -> root(so) }
            .toList()

    override fun iterator(): Iterator<SceneObject> = results().iterator()

    /** Tests whether a SceneObject matches the current predicate. */
    fun test(sceneObject: SceneObject): Boolean = root(sceneObject)

    /** Convenience: returns the nearest matching SceneObject, or null if none. */
    fun nearest(): SceneObject? =
        results().minByOrNull { so -> Distance.to(so) }

    /** Filters by one or more type ids. No-op when empty. */
    fun typeId(vararg typeIds: Int): SceneObjectQuery {
        if (typeIds.isEmpty()) return this
        val set = typeIds.toSet()
        val prev = root
        root = { t -> prev(t) && set.contains(t.typeId) }
        return this
    }

    /** Filters by one or more animation ids. No-op when empty. */
    fun animation(vararg animations: Int): SceneObjectQuery {
        if (animations.isEmpty()) return this
        val set = animations.toSet()
        val prev = root
        root = { t -> prev(t) && set.contains(t.animationId) }
        return this
    }

    /** Filters by hidden flag. Use `hidden(false)` for visible objects. */
    fun hidden(hidden: Boolean): SceneObjectQuery {
        val prev = root
        root = { t -> prev(t) && t.isHidden == hidden }
        return this
    }

    /** Filters by multi-type (definition). No-op when empty. */
    fun multiType(vararg sceneObjectDefinitions: SceneObjectDefinition): SceneObjectQuery {
        if (sceneObjectDefinitions.isEmpty()) return this
        val defs = sceneObjectDefinitions.toSet()
        val prev = root
        root = { t -> prev(t) && defs.contains(t.multiType) }
        return this
    }

    /** Filters by name using a custom predicate. No-op when empty. */
    fun name(spred: (String, CharSequence) -> Boolean, vararg names: String): SceneObjectQuery {
        if (names.isEmpty()) return this
        val prev = root
        root = { t ->
            val objName = t.name
            prev(t) && objName != null && names.any { n -> spred(n, objName) }
        }
        return this
    }

    /** Filters by exact name using String.contentEquals semantics. */
    fun name(vararg names: String): SceneObjectQuery = name(String::contentEquals, *names)

    /** Filters by regex Patterns (full match). No-op when empty. */
    fun name(vararg patterns: Pattern): SceneObjectQuery {
        if (patterns.isEmpty()) return this
        val prev = root
        root = { t ->
            val objName = t.name
            prev(t) && objName != null && Arrays.stream(patterns).anyMatch { p -> p.matcher(objName).matches() }
        }
        return this
    }

    /** Filters by options using a custom predicate. No-op when empty. */
    fun option(spred: (String, CharSequence) -> Boolean, vararg options: String): SceneObjectQuery {
        if (options.isEmpty()) return this
        val prev = root
        root = { t ->
            val objOptions = t.options
            prev(t) && objOptions != null && objOptions.isNotEmpty() && options.any { i ->
                objOptions.any { j -> j != null && spred(i, j as CharSequence) }
            }
        }
        return this
    }

    /** Filters by options using String.contentEquals semantics. */
    fun option(vararg option: String): SceneObjectQuery = option(String::contentEquals, *option)

    /** Filters by options using regex Patterns. No-op when empty. */
    fun option(vararg patterns: Pattern): SceneObjectQuery {
        if (patterns.isEmpty()) return this
        val prev = root
        root = { t ->
            val objOptions = t.options
            prev(t) && objOptions != null && objOptions.any { opt ->
                opt != null && Arrays.stream(patterns).anyMatch { p -> p.matcher(opt).matches() }
            }
        }
        return this
    }
}
