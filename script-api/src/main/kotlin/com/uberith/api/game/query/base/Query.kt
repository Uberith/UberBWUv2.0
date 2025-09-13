package com.uberith.api.game.query.base

import com.uberith.api.game.query.result.ResultSet

interface Query<T> : MutableIterable<T> {
    fun results(): ResultSet<T>
    fun test(t: T): Boolean
}

