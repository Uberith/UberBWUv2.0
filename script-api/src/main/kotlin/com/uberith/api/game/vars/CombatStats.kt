package com.uberith.api.game.vars


data class DamageStats(val mainHand: Int, val offHand: Int, val abilityDamage: Int)

object CombatStats {
    private const val VAR_MAIN = 715
    private const val VAR_OFF = 716
    fun read(): DamageStats {
        val mh = safeVar(VAR_MAIN)
        val ohStored = safeVar(VAR_OFF)
        val oh = ohStored / 2
        val ability = mh + (oh)
        return DamageStats(mh, oh, ability)
    }
    private fun safeVar(id: Int): Int = try {
        val dm = Class.forName("net.botwithus.rs3.game.vars.VarDomainType")
        val player = dm.getField("PLAYER").get(null)
        val vm = Class.forName("net.botwithus.rs3.game.vars.VarManager")
        val m = vm.getMethod("getVarValue", dm, Int::class.javaPrimitiveType)
        (m.invoke(null, player, id) as? Int) ?: 0
    } catch (_: Throwable) { 0 }
}
