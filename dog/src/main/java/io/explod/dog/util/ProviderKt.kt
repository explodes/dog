@file:OptIn(ExperimentalContracts::class)

package io.explod.dog.util

import java.lang.ref.SoftReference
import kotlin.contracts.ExperimentalContracts

/** Provider for lazy dependency inversion. */
fun interface Provider<T> {
    /** Get value to use. Could be a singleton, reusable, or new object. */
    fun get(): T
}

/** A provider that creates only one instance ever. */
fun <T> provideSingleton(factory: () -> T): Provider<T> {
    class LazyBox {
        val value by lazy { factory() }
    }

    val lazy = LazyBox()
    return Provider { lazy.value }
}

/** A provider that creates a new instance only if memory cleared an old soft reference. */
fun <T : Any> provideReusable(factory: () -> T): Provider<T> {
    val referenceLock = Any()
    var reference: SoftReference<T>? = null
    return Provider {
        synchronized(referenceLock) {
            val oldReference = reference
            val oldValue = oldReference?.get()
            if (oldReference == null || oldValue == null) {
                val newValue = factory()
                val newReference = SoftReference(newValue)
                reference = newReference
                newValue
            } else {
                oldValue
            }
        }
    }
}

/** A provider that creates a new instance every time. */
fun <T> provideInstances(factory: () -> T): Provider<T> {
    return Provider { factory() }
}
