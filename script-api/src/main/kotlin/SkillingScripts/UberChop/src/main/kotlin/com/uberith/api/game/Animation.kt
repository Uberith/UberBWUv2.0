package com.uberith.api.game

import net.botwithus.rs3.entities.LocalPlayer

object Animation {
    fun waitForIdle(timeoutMs: Long = 5000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val p = LocalPlayer.self() ?: return false
            if (p.animationId == -1) return true
            Thread.sleep(100)
        }
        return false
    }
}
