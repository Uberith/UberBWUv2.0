package com.uberith.suspendabledemo

import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.scripts.Info

@Info(
    name = "SuspendableDemo",
    description = "Demonstrates how SuspendableScript exposes permissive-style helpers.",
    version = "0.1.0",
    author = "Uberith"
)
class SuspendableDemo : SuspendableScript() {

    private enum class DemoState {
        STARTUP,
        GATHERING,
        BANKING
    }

    private var state: DemoState = DemoState.STARTUP

    override fun onActivation() {
        info("SuspendableDemo activated")
        switchState(DemoState.STARTUP)
    }

    override fun onDeactivation() {
        info("SuspendableDemo deactivated")
    }

    override suspend fun onLoop() {
        when (state) {
            DemoState.STARTUP -> startupPhase()
            DemoState.GATHERING -> gatheringPhase()
            DemoState.BANKING -> bankingPhase()
        }
    }

    private suspend fun startupPhase() {
        info("Startup phase entered")
        delay(5)
        switchState(DemoState.GATHERING)
    }

    private suspend fun gatheringPhase() {
        info("Pretending to gather resources: awaitTicks() comes from SuspendableScript")
        awaitTicks(3)
        warn("Simulated full backpack; moving to BANKING")
        switchState(DemoState.BANKING)
    }

    private suspend fun bankingPhase() {
        info("Banking complete; using delay() from permissive helpers before looping")
        delay(3)
        switchState(DemoState.GATHERING)
    }

    private fun switchState(next: DemoState) {
        if (state == next) {
            return
        }
        info("State -> ${next.name}")
        state = next
    }
}
