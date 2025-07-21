@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog_compose

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.Connection
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Ok
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Ok
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal data class Versioned<T>(val version: Long, val value: T) {

    fun updatedVersionNumber() = Versioned(version + 1, value)

    fun nextVersion(value: T) = Versioned(version + 1, value)

    companion object {

        fun <T> of(value: T, initialVersion: Long = 1L) = Versioned(initialVersion, value)
    }
}

internal class NullConnection() : Connection {
    override fun getPartialIdentity() = null

    override fun getFullIdentity() = null

    override suspend fun send(bytes: ByteArray): Result<Ok, FailureReason> = Ok(Ok)

    override suspend fun receive(buffer: ByteArray): Result<Int, FailureReason> = Ok(buffer.size)

    override fun close() = Unit

    override val name: String = "NullConnection"
    override val chainId: ChainId = ChainId(id.incrementAndFetch())

    companion object {
        private val id = AtomicLong(0)
    }
}
