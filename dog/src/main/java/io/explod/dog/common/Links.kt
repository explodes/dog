@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog.common

import android.content.Context
import io.explod.dog.conn.ChainId
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.FullIdentityLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.PartialIdentityLink
import io.explod.dog.conn.closeLink
import io.explod.dog.conn.handleJoin
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.UserInfo
import io.explod.dog.protocol.createFullIdentity
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Ok
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Err
import io.explod.dog.util.Result.Companion.Ok
import io.explod.loggly.Logger
import java.io.IOException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

abstract class IOPartialIdentityLink(
    protected val applicationContext: Context,
    protected val connection: LinkedConnection,
    protected val logger: Logger,
    private var currentPartialIdentity: PartialIdentity,
    private var currentFullIdentity: FullIdentity,
    protected val protocol: Protocol,
    protected val userInfo: UserInfo,
) : PartialIdentityLink() {

    protected val socketRef = AtomicReference<ReaderWriterCloser?>(null)

    override suspend fun advance(allowBonding: Boolean): Result<Link, FailureReason> {
        return if (isBonded()) {
            advanceAlreadyBonded()
        } else if (allowBonding) {
            advanceByBonding()
        } else {
            Err(FailureReason("Device not allowed to bond."))
        }
    }

    @Throws(IOException::class) protected abstract fun createSocket(): ReaderWriterCloser

    protected abstract suspend fun advanceByBonding(): Result<Link, FailureReason>

    protected fun advanceAlreadyBonded(): Result<Link, FailureReason> {
        var socket: ReaderWriterCloser? = null
        try {
            socket = createSocket()
            socketRef.store(socket)
            val localFullIdentity =
                createFullIdentity(applicationContext, userInfo, ConnectionType.BLUETOOTH)
            protocol
                .identify(socket.inputStream, socket.outputStream, localFullIdentity)
                .ok { fullIdentity ->
                    this@IOPartialIdentityLink.currentPartialIdentity = fullIdentity.partialIdentity
                    this@IOPartialIdentityLink.currentFullIdentity = fullIdentity
                    connection.notifyLinkIdentityChanged(this@IOPartialIdentityLink)
                }
                .err {
                    closeLink(connection, socket, logger, ConnectionState.ERROR)
                    return@advanceAlreadyBonded Err(it)
                }
            return createFullIdentityLink(socket)
                .ok { link -> connection.setLink(link, ConnectionState.OPENING) }
                .err { closeLink(connection, socket, logger, ConnectionState.ERROR) }
        } catch (ex: IOException) {
            closeLink(connection, socket, logger, ConnectionState.ERROR)
            val message = "IO error during identification: ${ex.message}"
            logger.error(message, ex)
            return Err(FailureReason(message))
        } catch (ex: Exception) {
            closeLink(connection, socket, logger, ConnectionState.ERROR)
            val message = "Generic error during identification: ${ex.message}"
            logger.error(message, ex)
            return Err(FailureReason(message))
        }
    }

    abstract fun createFullIdentityLink(socket: ReaderWriterCloser): Result<Link, FailureReason>

    override fun getFullIdentity(): FullIdentity {
        return currentFullIdentity
    }

    override fun getPartialIdentity(): PartialIdentity {
        return currentPartialIdentity
    }

    override suspend fun close() {
        val socket = socketRef.load()
        closeLink(connection, socket, logger, ConnectionState.CLOSED)
    }
}

abstract class IOFullIdentityLink(
    chainId: ChainId,
    protected val connection: LinkedConnection,
    protected val socket: ReaderWriterCloser,
    protected val logger: Logger,
    private val currentPartialIdentity: PartialIdentity,
    private val currentFullIdentity: FullIdentity,
    protected val protocol: Protocol,
) : FullIdentityLink(chainId) {

    override suspend fun advance(join: Protocol.Join): Result<ConnectedLink, FailureReason> {
        handleJoin(connection, socket, logger) {
                protocol.join(socket.inputStream, socket.outputStream, logger, join)
            }
            .err {
                return@advance Err(it)
            }
        return createConnectedLink()
            .ok { link -> connection.setLink(link, ConnectionState.CONNECTED) }
            .err { closeLink(connection, socket, logger, ConnectionState.ERROR) }
    }

    abstract fun createConnectedLink(): Result<ConnectedLink, FailureReason>

    override fun getFullIdentity(): FullIdentity {
        return currentFullIdentity
    }

    override fun getPartialIdentity(): PartialIdentity {
        return currentPartialIdentity
    }

    override suspend fun close() {
        closeLink(connection, socket, logger, ConnectionState.CLOSED)
    }
}

/** At least for now, the implementation is identical between Sever and Client. */
abstract class IOConnectedLink(
    chainId: ChainId,
    protected val connection: LinkedConnection,
    private val socket: ReaderWriterCloser,
    protected val logger: Logger,
    private val currentPartialIdentity: PartialIdentity,
    private val currentFullIdentity: FullIdentity,
) : ConnectedLink(chainId) {
    override suspend fun send(bytes: ByteArray): Result<Ok, FailureReason> {
        try {
            socket.outputStream.write(bytes)
            return Ok(Ok)
        } catch (ex: IOException) {
            close()
            return Err(FailureReason("IO error sending to device: ${ex.message}"))
        }
    }

    override suspend fun receive(bytes: ByteArray): Result<Int, FailureReason> {
        try {
            val size = socket.inputStream.read(bytes)
            return Ok(size)
        } catch (ex: IOException) {
            close()
            return Err(FailureReason("IO error sending to device: ${ex.message}"))
        }
    }

    override fun getFullIdentity(): FullIdentity {
        return currentFullIdentity
    }

    override fun getPartialIdentity(): PartialIdentity {
        return currentPartialIdentity
    }

    override suspend fun close() {
        closeLink(connection, socket, logger, ConnectionState.CLOSED)
    }
}
