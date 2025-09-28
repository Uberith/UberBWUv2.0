package com.uberith.uberchop.state

import net.botwithus.kxapi.permissive.PermissiveDSL
import net.botwithus.kxapi.permissive.StateEnum
import kotlin.reflect.KClass

enum class BotState(
    override val description: String,
    override val classz: KClass<out PermissiveDSL<*>>
) : StateEnum {
    CHOPPING("Chopping", Chopping::class),
    BANKING("Banking", Banking::class);

    override fun toString(): String = description
}
