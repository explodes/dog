@file:JvmName("ResultKt")
@file:OptIn(ExperimentalContracts::class)

package io.explod.dog.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Get the value of this left Either, or throw the right Throwable iff it is not-null. */
fun <Ok, Err : Throwable> Result<Ok, Err>.getOrThrow(): Ok {
    val (left, right) = this
    if (right != null) {
        throw right
    }
    return left!!
}

/** Transform A to change this from a Result<A, Err> to a Result<B, Err>. */
inline fun <A, B, Err> Result<A, Err>.mapOk(ok: (A) -> B): Result<B, Err> {
    contract { callsInPlace(ok, InvocationKind.AT_MOST_ONCE) }
    ok { original ->
        val nextValue = ok(original)
        return@mapOk Result.Ok(nextValue)
    }
    throw IllegalStateException("Should never get here")
}

/**
 * Map this A value to a Result<B, Err> but the new Result isn't a Result<Result<B, Err>, Err>, it
 * is a Result<B, Err>.
 */
inline fun <A, B, Err> Result<A, Err>.flatMapOk(ok: (A) -> Result<B, Err>): Result<B, Err> {
    contract { callsInPlace(ok, InvocationKind.AT_MOST_ONCE) }
    ok { original ->
        val nextResult = ok(original)
        return@flatMapOk nextResult
    }
    throw IllegalStateException("Should never get here")
}

/**
 * Map this A value to a Result<B, Err> and discard the B, returning the A error or a new Err.
 *
 * This is a shorthand for when processing different results with the same Err types but want to to
 * discard a value of an intermediate step.
 *
 * This is analogous to `ok` for `map`, in that a function is called with the value. We just
 * propagate the error in the chain.
 */
inline fun <A, B, Err> Result<A, Err>.flatOk(ok: (A) -> Result<B, Err>): Result<A, Err> {
    contract { callsInPlace(ok, InvocationKind.AT_MOST_ONCE) }
    ok { original ->
        val nextError = ok(original)
        return@flatOk nextError.mapOk { next -> original }
    }
    throw IllegalStateException("Should never get here")
}

/** Transform A to change this from a Result<Ok, A> to a Result<Ok, B>. */
inline fun <Ok, A, B> Result<Ok, A>.mapErr(err: (A) -> B): Result<Ok, B> {
    contract { callsInPlace(err, InvocationKind.AT_MOST_ONCE) }
    err {
        return@mapErr Result.Err(err(it))
    }
    throw IllegalStateException("Should never get here")
}

/**
 * Map this A error to a Result<Ok, Err> but the new Result isn't a Result<Ok, Result<Ok, B>>, it is
 * a Result<Ok, B>.
 */
inline fun <Ok, A, B> Result<Ok, A>.flatMapErr(err: (A) -> Result<Ok, B>): Result<Ok, B> {
    contract { callsInPlace(err, InvocationKind.AT_MOST_ONCE) }
    err {
        return@flatMapErr err(it)
    }
    throw IllegalStateException("Should never get here")
}

/**
 * Map this A error to a Result<Ok, B> and discard the B, returning the A error or a new Err.
 *
 * This is a shorthand for when processing different results with the same Ok types but want to to
 * discard an error of an intermediate step.
 *
 * Why? Don't know. But you can do this with Ok's using flatOk. Maybe errors produced from closing a
 * connection can be logged and dropped?
 */
inline fun <Ok, A, B> Result<Ok, A>.flatErr(err: (A) -> Result<Ok, B>): Result<Ok, A> {
    contract { callsInPlace(err, InvocationKind.AT_MOST_ONCE) }
    err { a: A ->
        val nextResult: Result<Ok, B> = err(a)
        return@flatErr nextResult.mapErr { b -> a }
    }
    throw IllegalStateException("Should never get here")
}
