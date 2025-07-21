@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog.conn

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/** An identifier shared with each Link in a chain of advances. */
@JvmInline
value class ChainId(val id: Long) {
    companion object {
        private val nextId = AtomicLong(0L)

        fun next(): ChainId {
            return ChainId(nextId.incrementAndFetch())
        }
    }
}
