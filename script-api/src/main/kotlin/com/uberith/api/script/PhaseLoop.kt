package com.uberith.api.script

import org.slf4j.Logger

/**
 * Tiny utility that drives enum-based phase handlers while handling transition logging.
 *
 * Usage:
 * ```kotlin
 * private val phases = PhaseLoop(Phase.READY, logger)
 *     .on(Phase.READY) { transitionTo(Phase.PREPARING); false }
 *     .on(Phase.PREPARING) { /* work */ }
 *
 * suspend fun tickPhases(): Boolean = phases.tick()
 * ```
 */
class PhaseLoop<P : Enum<P>>(
    initial: P,
    private val logger: Logger? = null
) {

    private val handlers = mutableMapOf<P, suspend PhaseScope<P>.() -> Boolean>()
    private var current: P = initial

    val phase: P
        get() = current

    fun on(phase: P, handler: suspend PhaseScope<P>.() -> Boolean): PhaseLoop<P> {
        handlers[phase] = handler
        return this
    }

    suspend fun tick(): Boolean {
        val handler = handlers[current] ?: return false
        val scope = PhaseScope(this, current)
        val handled = handler(scope)
        scope.pendingTransition?.let { (target, message) ->
            transition(target, message)
        }
        return handled
    }

    fun transition(target: P, message: String? = null) {
        if (target == current) return
        val previous = current
        current = target
        val logMessage = message ?: "Phase: $previous -> $target"
        logger?.info(logMessage)
    }

    class PhaseScope<P : Enum<P>>(private val loop: PhaseLoop<P>, val phase: P) {
        internal var pendingTransition: Pair<P, String?>? = null

        fun transitionTo(target: P, message: String? = null) {
            pendingTransition = target to message
        }

        fun transitionNow(target: P, message: String? = null) {
            loop.transition(target, message)
        }
    }
}
