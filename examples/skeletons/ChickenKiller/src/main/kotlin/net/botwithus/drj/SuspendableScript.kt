package net.botwithus.drj

import net.botwithus.rs3.client.Client
import net.botwithus.scripts.Script
import kotlin.coroutines.*

abstract class SuspendableScript : Script() {

    private var continuation: Continuation<Unit>? = null
    private var isSuspended = false
    private var targetTick = 0

    abstract suspend fun onLoop()

    override fun run() {
        if (isSuspended && Client.getServerTick() >= targetTick) {
            isSuspended = false
            val current = continuation
            continuation = null
            current?.resume(Unit)
        } else if (continuation == null) {
            startCoroutine()
        }
    }

    suspend fun wait(ticks: Int) {
        suspendCoroutine { cont ->
            continuation = cont
            isSuspended = true
            targetTick = Client.getServerTick() + ticks
        }
    }

    suspend fun waitUntil(timeout: Int = 5, condition: () -> Boolean) {
        val timeoutTick = Client.getServerTick() + timeout
        while (!condition()) {
            if (Client.getServerTick() >= timeoutTick) return
            wait(1)
        }
    }

    private val coroutineBlock: suspend () -> Unit = {
        while (true) {
            onLoop()
            wait(1)
        }
    }

    private fun startCoroutine() {
        continuation = coroutineBlock.createCoroutine(object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                result.onFailure { throwable -> println(throwable) }
            }
        })
        continuation?.resume(Unit)
    }
}

