package io.explod.dog.testing

import io.explod.dog.conn.Connection
import io.explod.dog.protocol.DedupNonce
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.Protocol.Join.ACCEPT
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Ok
import io.explod.loggly.Logger
import java.io.InputStream
import java.io.OutputStream
import kotlin.random.Random

class FakeProtocol(
     val remoteIdentity: FullIdentity,
     val remoteDedupNonce: DedupNonce = DedupNonce(Random.nextLong()),
) : Protocol {

    override fun identify(
        inputStream: InputStream,
        outputStream: OutputStream,
        fullIdentity: FullIdentity,
    ): Result<FullIdentity, FailureReason> {
        return Ok(remoteIdentity)
    }

    override fun join(
        inputStream: InputStream,
        outputStream: OutputStream,
        logger: Logger,
        join: Protocol.Join,
    ): Result<Protocol.Join, FailureReason> {
        return Ok(ACCEPT)
    }

    override suspend fun shareDeduplicationIdentity(
        connection: Connection,
        logger: Logger,
        localNonce: DedupNonce,
    ): Result<DedupNonce, FailureReason> {
        return Ok(remoteDedupNonce)
    }
}
