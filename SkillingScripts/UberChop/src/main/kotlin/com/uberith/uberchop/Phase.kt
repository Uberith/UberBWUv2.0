package com.uberith.uberchop

// Top-level state enum to avoid nested-class loading issues with ServiceLoader
enum class Phase {
    READY,
    PREPARING,
    CHOPPING,
    BANKING
}

