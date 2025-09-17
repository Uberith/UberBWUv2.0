package com.uberith.api.game.skills.woodcutting.internal

/**
 * Defines how gathered logs are handled once the backpack is full.
 */
enum class LogHandlingMode {
    /** Deposit logs into the bank. */
    BANK,

    /** Drop logs on the ground. */
    DROP,

    /** Light logs using the player's tinderbox. */
    BURN,

    /** Open the fletching interface and craft from carried logs. */
    FLETCH
}
