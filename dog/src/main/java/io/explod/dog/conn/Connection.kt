package io.explod.dog.conn

import androidx.annotation.CheckResult
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Ok
import io.explod.dog.util.Result
import java.io.Closeable

interface Connection : Closeable {

    /** Unique identifier for this connection. */
    val chainId: ChainId

    /** User-friendly name of this connection. */
    val name: String
        get() {
            val deviceType = fromIdentity<DeviceType?> { deviceType }
            val connectionType = fromIdentity { connectionType }
            val name = fromIdentity { name } ?: createName(deviceType, connectionType)
            return name
        }

    /**
     * Sends bytes to the remote device. Blocks until sent. Returns error if connection is closed.
     */
    @CheckResult suspend fun send(bytes: ByteArray): Result<Ok, FailureReason>

    /**
     * Receive bytes from the remote device. Blocks until some bytes are read. Returns the number of
     * bytes read. Returns error if connection is closed.
     */
    @CheckResult suspend fun receive(buffer: ByteArray): Result<Int, FailureReason>

    /** Get the current PartialIdentity information. */
    fun getPartialIdentity(): PartialIdentity?

    /** Get the current FullIdentity information. */
    fun getFullIdentity(): FullIdentity?
}
