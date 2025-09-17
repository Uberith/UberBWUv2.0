package com.uberith.api.script

import org.slf4j.Logger

/**
 * Generic phase-driven script base with persistent settings, runtime tracking, and a DSL loop.
 *
 * @param T settings model type
 * @param P enum representing script phases
 * @param C context object constructed on activation and exposed during loops
 */
abstract class PhasedPersistentScript<T : Any, P : Enum<P>, C : Any>(
    moduleName: String,
    settingsClass: Class<T>,
    defaultFactory: () -> T,
    private val initialPhase: P,
    private val phaseLogger: Logger? = null
) : PersistentSettingsScript<T>(moduleName, settingsClass, defaultFactory) {

    protected val runtime = RuntimeTracker()
    protected val phases = PhaseLoop(initialPhase, phaseLogger)

    private var loopStarted = false
    private lateinit var loopContext: C
    private val loop = ScriptLoop()

    /** Create the context object used throughout the script lifecycle (per activation). */
    protected abstract fun createContext(): C

    /** Optional hook that runs once before the main loop starts. Return true to skip the current tick. */
    protected open suspend fun ScriptLoop.onStart(): Boolean = false

    /** Primary loop body. Return true to skip the default one-tick wait. */
    protected abstract suspend fun ScriptLoop.onTick(): Boolean

    override fun onActivation() {
        super.onActivation()
        runtime.start()
        loopContext = createContext()
        loopStarted = false
    }

    override fun onDeactivation() {
        runtime.stop()
        super.onDeactivation()
    }

    override suspend fun onLoop() {
        runtime.mark()
        if (!loopStarted) {
            loopStarted = true
            if (loop.onStart()) return
        }
        if (loop.onTick()) return
        awaitTicks(1)
    }

    protected inner class ScriptLoop internal constructor() {
        val context: C
            get() = loopContext

        fun currentPhase(): P = phases.phase

        fun transitionTo(target: P, message: String? = null): PhaseDecision<P> =
            PhaseDecision(transition = target, handled = true, message = message)

        fun stay(): PhaseDecision<P> = PhaseDecision(handled = true)

        fun repeat(): PhaseDecision<P> = PhaseDecision(handled = false)

        suspend fun phaseLoop(handler: suspend PhaseMachine.() -> PhaseDecision<P>): Boolean {
            val decision = handler(PhaseMachine())
            decision.transition?.let { phases.transition(it, decision.message) }
            return decision.handled
        }

        inner class PhaseMachine internal constructor() {
            val phase: P
                get() = phases.phase

            val context: C
                get() = loopContext

            fun transitionTo(target: P, message: String? = null): PhaseDecision<P> =
                this@ScriptLoop.transitionTo(target, message)

            fun stay(): PhaseDecision<P> = this@ScriptLoop.stay()

            fun repeat(): PhaseDecision<P> = this@ScriptLoop.repeat()
        }
    }

    protected data class PhaseDecision<P : Enum<P>>(
        val transition: P? = null,
        val handled: Boolean,
        val message: String? = null
    )
}
