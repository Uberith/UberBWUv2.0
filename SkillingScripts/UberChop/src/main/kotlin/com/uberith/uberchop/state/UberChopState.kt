package com.uberith.uberchop.state

import com.uberith.uberchop.UberChop
import net.botwithus.kxapi.permissive.PermissiveDSL

abstract class UberChopState(
    script: UberChop,
    name: String
) : PermissiveDSL<UberChop>(script, name) {

    protected val bot: UberChop = script
}

