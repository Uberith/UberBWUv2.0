package com.uberith.api.game.world

import com.uberith.api.game.world.internal.SceneObjectRef
import com.uberith.api.game.world.internal.SceneObjects
import java.util.Arrays
import java.util.regex.Pattern

/**
 * Kotlin mirror of the external SceneObjectQuery fluent API, operating on [SceneObjectRef].
 *
 * This avoids direct dependencies on external client libraries while preserving a familiar
 * query surface. Results are produced from [SceneObjects.provider].[all]. If the provider yields
 * objects in nearest-first order, [EntityResultSet.nearest] will reflect that ordering.
 */
class SceneObjectQuery : Iterable<Any> {

    private var root: (com.uberith.api.game.world.internal.SceneObjectRef) -> Boolean = { true }

    companion object {
        fun newQuery(): SceneObjectQuery = SceneObjectQuery()
    }

    fun results(): List<Any> {
        val list = com.uberith.api.game.world.internal.SceneObjects.provider
            .all()
            .filter { ref -> root(ref) }
            .map { it as Any }
            .toList()
        return list
    }

    override fun iterator(): Iterator<Any> = results().iterator()

    fun test(sceneObject: Any): Boolean =
        (sceneObject as? com.uberith.api.game.world.internal.SceneObjectRef)?.let(root) ?: false

    fun typeId(vararg typeIds: Int): SceneObjectQuery {
        if (typeIds.isEmpty()) return this
        val set = typeIds.toSet()
        root = { t -> root(t) && t.typeId != null && set.contains(t.typeId) }
        return this
    }

    fun animation(vararg animations: Int): SceneObjectQuery {
        if (animations.isEmpty()) return this
        val set = animations.toSet()
        root = { t -> root(t) && t.animationId != null && set.contains(t.animationId) }
        return this
    }

    fun hidden(hidden: Boolean): SceneObjectQuery {
        root = { t -> root(t) && t.hidden == hidden }
        return this
    }

    fun multiType(vararg sceneObjectDefinitions: Any): SceneObjectQuery {
        if (sceneObjectDefinitions.isEmpty()) return this
        val defs = sceneObjectDefinitions.toSet()
        root = { t -> root(t) && t.multiType != null && defs.contains(t.multiType) }
        return this
    }

    fun name(spred: (String, CharSequence) -> Boolean, vararg names: String): SceneObjectQuery {
        if (names.isEmpty()) return this
        root = { t ->
            val objName = t.name
            root(t) && objName != null && names.any { n -> spred(n, objName) }
        }
        return this
    }

    fun name(vararg names: String): SceneObjectQuery = name(String::contentEquals, *names)

    fun name(vararg patterns: Pattern): SceneObjectQuery {
        if (patterns.isEmpty()) return this
        root = { t ->
            val objName = t.name
            root(t) && objName != null && Arrays.stream(patterns).anyMatch { p -> p.matcher(objName).matches() }
        }
        return this
    }

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

    fun option(vararg option: String): SceneObjectQuery = option(String::contentEquals, *option)

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
