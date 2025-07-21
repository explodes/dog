@file:OptIn(ExperimentalContracts::class)

package io.explod.dog.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Encapsulate a value, or values grouped by class, so that reading the values requires them to be
 * locked together.
 */
class Mutexed<T>(var value: T) {
    private val valueLock = Mutex()

    suspend fun <R> locked(action: suspend LockScope.(T) -> R): R {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
        return valueLock.withLock { LockScope().action(value) }
    }

    inner class LockScope {
        fun setValue(value: T) {
            this@Mutexed.value = value
        }
    }
}

/** Create a new Locked object from a factory method. */
inline fun <T> mutexed(factory: () -> T): Mutexed<T> {
    contract { callsInPlace(factory, InvocationKind.EXACTLY_ONCE) }
    return Mutexed(factory())
}

/** Create a new Locked object with a null initial value. */
fun <T> mutexedNull(): Mutexed<T?> {
    return mutexed { null }
}
