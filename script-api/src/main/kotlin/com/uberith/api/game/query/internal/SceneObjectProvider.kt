package com.uberith.api.game.query.internal

import java.util.regex.Pattern

/**
 * Provider interface exposing scene objects to the query layer.
 *
 * The default methods derive convenience lookups from [all]. Implementations may
 * override them for efficiency.
 */
internal interface SceneObjectProvider {
    /** Returns all scene objects visible to the client. Order should be nearest-first if possible. */
    fun all(): Sequence<SceneObjectRef>

    /** Returns the nearest visible object matching [namePattern], or null. */
    fun nearestByName(namePattern: Pattern, hidden: Boolean = false): SceneObjectRef? {
        val pred: (SceneObjectRef) -> Boolean = { ref ->
            val objName = ref.name
            ref.hidden == hidden && objName != null && namePattern.matcher(objName).matches()
        }
        return all().firstOrNull(pred)
    }

    /** Returns the count of visible objects matching [namePattern]. */
    fun countByName(namePattern: Pattern, hidden: Boolean = false): Int {
        val pred: (SceneObjectRef) -> Boolean = { ref ->
            val objName = ref.name
            ref.hidden == hidden && objName != null && namePattern.matcher(objName).matches()
        }
        return all().count(pred)
    }
}
