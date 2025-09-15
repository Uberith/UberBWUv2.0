package com.uberith.api

import net.botwithus.modules.BotModule
import net.botwithus.ui.WorkspaceManager

/**
 * Initializes BotWithUs workspace and module persistence on first use.
 * Loaded once per JVM when referenced.
 */
object BWUInit {
    init {
        // Load persisted workspaces and modules into memory
        runCatching { WorkspaceManager.load() }
        runCatching { BotModule.load() }
    }
}

