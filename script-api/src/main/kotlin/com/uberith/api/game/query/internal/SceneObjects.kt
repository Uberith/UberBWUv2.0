package com.uberith.api.game.query.internal

import java.util.regex.Pattern

/**
 * Global access point to the installed [SceneObjectProvider]. A no-op default is provided.
 */
internal object SceneObjects {
    @Volatile
    var provider: SceneObjectProvider = object : SceneObjectProvider {
        override fun all(): Sequence<SceneObjectRef> = emptySequence()
        override fun nearestByName(namePattern: Pattern, hidden: Boolean): SceneObjectRef? = null
        override fun countByName(namePattern: Pattern, hidden: Boolean): Int = 0
    }

    /** Installs a concrete provider implementation. */
    fun install(p: SceneObjectProvider) { provider = p }
}
