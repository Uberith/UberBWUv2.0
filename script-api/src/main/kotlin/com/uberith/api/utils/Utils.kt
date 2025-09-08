package com.uberith.api.utils
import java.util.concurrent.TimeUnit

object Utils {
    fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(FuzzyRandom.shortWait().toLong())
        }
        return false
    }
    fun waitMs(ms: Long) { Thread.sleep(ms) }
    fun msToHms(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
