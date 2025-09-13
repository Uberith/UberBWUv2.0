package com.uberith.api.game.query.internal

/**
 * Lightweight, client-agnostic representation of a scene object.
 * Host adapters should populate these fields from their client APIs.
 */
internal interface SceneObjectRef {
    /** Optional unique object id (not the type id). */
    val id: Int?
    /** Optional type identifier. */
    val typeId: Int?
    /** Optional current animation id. */
    val animationId: Int?
    /** Display name, if available. */
    val name: String?
    /** Whether the object is currently hidden. */
    val hidden: Boolean
    /** Action options (e.g., "Chop down"). */
    val options: List<String?>
    /** Multi-type descriptor if your client exposes it (opaque). */
    val multiType: Any?

    /** Sends an interaction action. Returns a positive value on success. */
    fun interact(action: String): Int
}
