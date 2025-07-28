package io.explod.dog.testing

import io.explod.dog.util.FailureReason
import io.explod.dog.util.Result
import org.junit.Assert.fail

fun <A, B> Result<A, B>.notOk(): Result<A, B> {
    return ok { fail("Got unexpected OK: ${it.message()}") }
}

fun <A, B> Result<A, B>.notErr(): Result<A, B> {
    return err { fail("Got unexpected ERR: ${it.message()}") }
}

fun <A, B> Result<A, B>.isOk(assertions: ((A) -> Unit) = {}): Result<A, B> {
    return notErr().ok {
        println("Got expected OK: ${it.message()}")
        assertions(it)
    }
}

fun <A, B> Result<A, B>.isErr(assertions: ((B) -> Unit) = {}): Result<A, B> {
    return notOk().err {
        println("Got expected ERR: ${it.message()}")
        assertions(it)
    }
}

private fun Any?.message(): String? {
    return when (this) {
        is Throwable -> message
        is FailureReason -> message
        else -> toString()
    }
}
