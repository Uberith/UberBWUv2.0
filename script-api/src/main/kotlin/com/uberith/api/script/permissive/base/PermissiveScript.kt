package com.uberith.api.script.permissive.base

open class PermissiveScript {
    open fun info(message: String) {}
    open fun warn(message: String) {}
    open fun delay(ticks: Int) {}
}
