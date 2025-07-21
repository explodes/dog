@file:OptIn(ExperimentalContracts::class)

package io.explod.dog.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Class representing one of two disjoint possibilities. */
sealed class Result<Ok, Err> {

    /** Decompose the Ok side of this Result. */
    abstract operator fun component1(): Ok?

    /** Decompose the Err side of this Result. */
    abstract operator fun component2(): Err?

    /** Apply a function to the Ok side of this Result if it exists. */
    inline fun ok(ok: (Ok) -> Unit): Result<Ok, Err> {
        contract { callsInPlace(ok, InvocationKind.AT_MOST_ONCE) }
        if (this is OkImpl) {
            ok(this.ok)
        }
        return this
    }

    /** Apply a function to the Err side of this Result if it exists. */
    inline fun err(err: (Err) -> Unit): Result<Ok, Err> {
        contract { callsInPlace(err, InvocationKind.AT_MOST_ONCE) }
        if (this is ErrImpl) {
            err(this.err)
        }
        return this
    }

    companion object {
        /** Construct an Result from a Ok value. */
        @Suppress("FunctionName")
        fun <Ok, Err> Ok(ok: Ok): Result<Ok, Err> {
            return OkImpl(ok)
        }

        /** Construct an Result from a Err value. */
        @Suppress("FunctionName")
        fun <Ok, Err> Err(err: Err): Result<Ok, Err> {
            return ErrImpl(err)
        }
    }
}

class OkImpl<Ok, Err>(val ok: Ok) : Result<Ok, Err>() {
    override operator fun component1(): Ok? = ok

    override operator fun component2(): Err? = null

    override fun toString(): String {
        return "Ok($ok)"
    }
}

class ErrImpl<Ok, Err>(val err: Err) : Result<Ok, Err>() {
    override operator fun component1(): Ok? = null

    override operator fun component2(): Err? = err

    override fun toString(): String {
        return "Err($err)"
    }
}
