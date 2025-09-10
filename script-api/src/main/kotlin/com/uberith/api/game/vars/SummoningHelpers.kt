package com.uberith.api.game.vars


data class SummoningStatus(
    val timeMinutes: Int?,
    val halfMinuteFlag: Boolean?,
    val storedScrolls: Int?,
    val pouchItemId: Int?,
    val summoningPoints: Int?,
    val specialPoints: Int?,
    val autoFireAttacks: Int?,
    val autoFireSeconds: Int?,
    val combatMode: Int?,
    val lifePoints: Int?,
    val maxLifePoints: Int?,
    val spellPoints: Int?,
    val spiritCapeUnlocked: Boolean?,
    val featsActive: Boolean?
)

object SummoningHelpers {
    fun read(): SummoningStatus {
        fun vb(id: Int): Int? = try {
            val cls = Class.forName("net.botwithus.rs3.game.vars.VarManager")
            val m = cls.getMethod("getVarbitValue", Int::class.javaPrimitiveType)
            (m.invoke(null, id) as? Int)
        } catch (_: Throwable) { null }
        fun vp(id: Int): Int? = try {
            val dm = Class.forName("net.botwithus.rs3.game.vars.VarDomainType")
            val player = dm.getField("PLAYER").get(null)
            val vm = Class.forName("net.botwithus.rs3.game.vars.VarManager")
            val m = vm.getMethod("getVarValue", dm, Int::class.javaPrimitiveType)
            (m.invoke(null, player, id) as? Int)
        } catch (_: Throwable) { null }

        return SummoningStatus(
            timeMinutes = vb(6055),
            halfMinuteFlag = vb(6054)?.let { it == 1 },
            storedScrolls = vb(25412),
            pouchItemId = vp(1831),
            summoningPoints = vb(41524),
            specialPoints = vb(26474),
            autoFireAttacks = vb(25413),
            autoFireSeconds = vb(49035),
            combatMode = vb(23196),
            lifePoints = vb(19034),
            maxLifePoints = vb(27403),
            spellPoints = vp(1787),
            spiritCapeUnlocked = vb(44137)?.let { it == 1 },
            featsActive = vb(45522)?.let { it == 1 }
        )
    }
}
