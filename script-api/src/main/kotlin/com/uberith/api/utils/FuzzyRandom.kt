package com.uberith.api.utils

import java.security.SecureRandom
import kotlin.math.round

object FuzzyRandom {
    private val rnd = SecureRandom()
    fun shortWait(): Int = 300 + rnd.nextInt(250)
    fun mediumWait(): Int = 700 + rnd.nextInt(600)
    fun longWait(): Int = 1200 + rnd.nextInt(1200)
    fun between(min: Int, max: Int): Int {
        if (max <= min) return min
        val span = max - min
        val g = rnd.nextGaussian() * 0.3 + 0.5
        val v = (min + span * g).coerceIn(min.toDouble(), max.toDouble())
        return round(v).toInt()
    }
}

