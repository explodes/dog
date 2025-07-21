@file:OptIn(ExperimentalContracts::class)

package io.explod.dog.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Encapsulate a value, or values grouped by class, so that reading the values requires them to be
 * locked together.
 */
class Locked<T>(var value: T) {
    private val valueLock = Any()

    private val scope = LockScope()

    fun <R> locked(action: LockScope.(T) -> R): R {
        contract { callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
        return synchronized(valueLock) { scope.action(value) }
    }

    inner class LockScope {
        fun setValue(value: T) {
            this@Locked.value = value
        }
    }
}

/** Create a new Locked object from a factory method. */
inline fun <T> locked(factory: () -> T): Locked<T> {
    contract { callsInPlace(factory, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
    return Locked(factory())
}

/** Create a new Locked object with a null initial value. */
fun <T> lockedNull(): Locked<T?> {
    return locked { null }
}
