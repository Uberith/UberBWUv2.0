package com.uberith.api.script

import com.uberith.api.game.skills.woodcutting.WoodcuttingSession
import org.slf4j.Logger

/**
 * Base class for woodcutting-focused scripts with lifecycle helpers and a phase loop DSL.
 */
abstract class WoodcuttingScript<T : Any, P : Enum<P>>(
    moduleName: String,
    settingsClass: Class<T>,
    defaultFactory: () -> T,
    private val initialPhase: P,
    private val phaseLogger: Logger? = null
) : PersistentSettingsScript<T>(moduleName, settingsClass, defaultFactory) {

    protected val runtime = RuntimeTracker()
    protected val phases = PhaseLoop(initialPhase, phaseLogger)

    private var loopStarted = false
    private val loopContext = ScriptLoop()

    protected abstract fun createWoodcuttingSession(): WoodcuttingSession

    private lateinit var woodcuttingSession: WoodcuttingSession

    protected open suspend fun ScriptLoop.onStart(): Boolean = false

    protected abstract suspend fun ScriptLoop.onTick(): Boolean

    override fun onActivation() {
        super.onActivation()
        runtime.start()
        woodcuttingSession = createWoodcuttingSession()
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
            if (loopContext.onStart()) {
                return
            }
        }
        if (loopContext.onTick()) {
            return
        }
        awaitTicks(1)
    }

    protected inner class ScriptLoop internal constructor() {
        val woodcutting: WoodcuttingSession
            get() = woodcuttingSession

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

            val woodcutting: WoodcuttingSession
                get() = woodcuttingSession

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
