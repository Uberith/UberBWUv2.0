package com.uberith.api

import net.botwithus.rs3.client.Client
import net.botwithus.scripts.Script
import kotlin.coroutines.createCoroutine

/**
 * Suspendable tick-driven script base.
 * Use onLoop() and wait()/waitUntil() for precise tick scheduling.
 */
abstract class SuspendableScript : Script() {

    private var continuation: kotlin.coroutines.Continuation<Unit>? = null
    private var isSuspended = false
    private var targetTick = 0

    abstract suspend fun onLoop()

    override fun run() {
        if (isSuspended && Client.getServerTick() >= targetTick) {
            isSuspended = false
            val current = continuation
            continuation = null
            current?.resumeWith(Result.success(Unit))
        } else if (continuation == null) {
            startCoroutine()
        }
    }

    suspend fun awaitTicks(ticks: Int) {
        kotlin.coroutines.suspendCoroutine<Unit> { cont ->
            continuation = cont
            isSuspended = true
            targetTick = Client.getServerTick() + ticks
        }
    }

    suspend fun awaitUntil(timeout: Int = 5, condition: () -> Boolean) {
        val timeoutTick = Client.getServerTick() + timeout
        while (!condition()) {
            if (Client.getServerTick() >= timeoutTick) return
            awaitTicks(1)
        }
    }

    // Kotlin-facing aliases preserving the original example names while avoiding JVM method name clashes
    @JvmName("waitTicksAlias")
    @Suppress("unused")
    suspend fun wait(ticks: Int) = awaitTicks(ticks)

    @JvmName("waitUntilAlias")
    @Suppress("unused")
    suspend fun waitUntil(timeout: Int = 5, condition: () -> Boolean) = awaitUntil(timeout, condition)

    private val coroutineBlock: suspend () -> Unit = {
        while (true) {
            onLoop()
            awaitTicks(1)
        }
    }

    private fun startCoroutine() {
        val completion = object : kotlin.coroutines.Continuation<Unit> {
            override val context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                result.onFailure { throwable -> println(throwable) }
            }
        }
        val c = coroutineBlock.createCoroutine(completion)
        c.resumeWith(Result.success(Unit))
    }
}
