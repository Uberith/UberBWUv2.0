package com.uberith.api.game.vars

object Prayers {
    private const val CURSES_TOGGLE = 16789
    fun isCursesEnabled(): Boolean = isActive(CURSES_TOGGLE)
    fun isActive(varbitId: Int): Boolean = try {
        val cls = Class.forName("net.botwithus.rs3.game.vars.VarManager")
        val m = cls.getMethod("getVarbitValue", Int::class.javaPrimitiveType)
        (m.invoke(null, varbitId) as? Int) == 1
    } catch (_: Throwable) { false }
}
