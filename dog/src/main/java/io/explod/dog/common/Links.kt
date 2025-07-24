@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog.common

import android.content.Context
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.IdentifiedLink
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.UnidentifiedLink
import io.explod.dog.conn.closeLink
import io.explod.dog.conn.handleJoin
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.UserInfo
import io.explod.dog.protocol.createIdentity
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

abstract class RwcUnidentifiedLink(
    protected val applicationContext: Context,
    protected val connection: LinkedConnection,
    protected val logger: Logger,
    private var currentRemoteIdentity: Identity,
    protected val protocol: Protocol,
    protected val userInfo: UserInfo,
) : UnidentifiedLink(connection.chainId) {

    protected val rwcRef = AtomicReference<ReaderWriterCloser?>(null)

    override suspend fun advance(allowPairing: Boolean): Result<IdentifiedLink, FailureReason> {
        return if (isPaired()) {
            advancePaired()
        } else if (allowPairing) {
            advancePairing()
        } else {
            Err(FailureReason("Device not allowed to pair."))
        }
    }

    /** Create a connected socket to R/W with. */
    @Throws(IOException::class)
    protected abstract fun createReaderWriterCloser(): ReaderWriterCloser

    /** Advance using out-of-band pairing followed up by identifying with a call to advancePaired() */
    protected abstract suspend fun advancePairing(): Result<IdentifiedLink, FailureReason>

    protected fun advancePaired(): Result<IdentifiedLink, FailureReason> {
        var rwc: ReaderWriterCloser? = null
        try {
            rwc = createReaderWriterCloser()
            rwcRef.store(rwc)
            val localIdentity =
                createIdentity(applicationContext, userInfo, ConnectionType.BLUETOOTH)
            protocol
                .identify(rwc.inputStream, rwc.outputStream, localIdentity)
                .ok { remoteIdentity ->
                    this@RwcUnidentifiedLink.currentRemoteIdentity = remoteIdentity
                    connection.notifyLinkIdentityChanged(this@RwcUnidentifiedLink)
                }
                .err {
                    closeLink(connection, rwc, logger, ConnectionState.ERROR)
                    return@advancePaired Err(it)
                }
            return createFullIdentityLink(rwc)
                .ok { link -> connection.setLink(link, ConnectionState.OPENING) }
                .err { closeLink(connection, rwc, logger, ConnectionState.ERROR) }
        } catch (ex: IOException) {
            closeLink(connection, rwc, logger, ConnectionState.ERROR)
            val message = "IO error during identification: ${ex.message}"
            logger.error(message, ex)
            return Err(FailureReason(message))
        } catch (ex: Exception) {
            closeLink(connection, rwc, logger, ConnectionState.ERROR)
            val message = "Generic error during identification: ${ex.message}"
            logger.error(message, ex)
            return Err(FailureReason(message))
        }
    }

    private fun createFullIdentityLink(
        socket: ReaderWriterCloser
    ): Result<IdentifiedLink, FailureReason> {
        return Ok(
            RwcIdentifiedLink(
                connection = connection,
                rwc = socket,
                logger = logger,
                currentRemoteIdentity = getIdentity(),
                protocol = protocol,
            )
        )
    }

    override fun getIdentity(): Identity {
        return currentRemoteIdentity
    }

    override suspend fun close() {
        val socket = rwcRef.load()
        closeLink(connection, socket, logger, ConnectionState.CLOSED)
    }

    override fun toString(): String {
        val rwc = rwcRef.load()
        return "RwcUnidentifiedLink(conn=$rwc,remote=${getIdentity()})"
    }
}

 class RwcIdentifiedLink(
     private val connection: LinkedConnection,
     private val rwc: ReaderWriterCloser,
     private val logger: Logger,
     private val currentRemoteIdentity: Identity,
     private val protocol: Protocol,
) : IdentifiedLink(connection.chainId) {

    override suspend fun advance(join: Protocol.Join): Result<ConnectedLink, FailureReason> {
        handleJoin(connection, rwc, logger) {
            protocol.join(rwc.inputStream, rwc.outputStream, logger, join)
        }
            .err {
                return@advance Err(it)
            }
        return createConnectedLink()
            .ok { link -> connection.setLink(link, ConnectionState.CONNECTED) }
            .err { closeLink(connection, rwc, logger, ConnectionState.ERROR) }
    }

    private fun createConnectedLink(): Result<ConnectedLink, FailureReason> {
        return Ok(
            RwcConnectedLink(
                connection = connection,
                rwc = rwc,
                logger = logger,
                currentRemoteIdentity = currentRemoteIdentity,
            )
        )
    }

    override fun getIdentity(): Identity {
        return currentRemoteIdentity
    }

    override suspend fun close() {
        closeLink(connection, rwc, logger, ConnectionState.CLOSED)
    }

     override fun toString(): String {
         return "RwcIdentifiedLink(conn=$rwc,remote=$currentRemoteIdentity)"
     }
}

/** At least for now, the implementation is identical between Sever and Client. */
 class RwcConnectedLink(
    private val connection: LinkedConnection,
    private val rwc: ReaderWriterCloser,
    private val logger: Logger,
    private val currentRemoteIdentity: Identity,
) : ConnectedLink(connection.chainId) {
    override suspend fun send(bytes: ByteArray): Result<Ok, FailureReason> {
        try {
            rwc.outputStream.write(bytes)
            return Ok(Ok)
        } catch (ex: IOException) {
            close()
            return Err(FailureReason("IO error sending to device: ${ex.message}"))
        }
    }

    override suspend fun receive(bytes: ByteArray): Result<Int, FailureReason> {
        try {
            val size = rwc.inputStream.read(bytes)
            return Ok(size)
        } catch (ex: IOException) {
            close()
            return Err(FailureReason("IO error sending to device: ${ex.message}"))
        }
    }

    override fun getIdentity(): Identity {
        return currentRemoteIdentity
    }

    override suspend fun close() {
        closeLink(connection, rwc, logger, ConnectionState.CLOSED)
    }

    override fun toString(): String {
        return "RwcConnectedLink(conn=$rwc,remote=$currentRemoteIdentity)"
    }
}
