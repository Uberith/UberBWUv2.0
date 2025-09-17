package com.uberith.api.script

open class PermissiveScript {
    open fun info(message: String) {}
    open fun warn(message: String) {}
    open fun delay(ticks: Int) {}
}