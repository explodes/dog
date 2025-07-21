@file:OptIn(ExperimentalContracts::class)

package io.explod.dog.conn

import io.explod.dog.protocol.Protocol
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Ok
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Err
import io.explod.dog.util.Result.Companion.Ok
import io.explod.loggly.Logger
import java.io.Closeable
import java.io.IOException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Close a link correctly. */
internal fun closeLink(
    connection: LinkedConnection,
    socket: Closeable?,
    logger: Logger,
    connectionState: ConnectionState,
) {
    if (socket != null) {
        try {
            socket.close()
        } catch (ex: IOException) {
            logger.error("Error while closing socket: ${ex.message}")
        }
    }
    connection.stateListeners.set(connectionState)
}

/**
 * Perform a join/reject sequence.
 *
 * Closes connection on rejection and on error. Returns Ok(Ok) on a successful join.
 */
internal inline fun handleJoin(
    connection: LinkedConnection,
    closeable: Closeable,
    logger: Logger,
    performJoin: () -> Result<Protocol.Join, FailureReason>,
): Result<Ok, FailureReason> {
    contract { callsInPlace(performJoin, InvocationKind.AT_MOST_ONCE) }
    try {
        val join = performJoin()
        join
            .err { failureReason ->
                return Err(failureReason)
            }
            .ok {
                return when (it) {
                    Protocol.Join.ACCEPT -> Ok(Ok)
                    Protocol.Join.REJECT -> {
                        logger.error("Join rejected.")
                        closeLink(connection, closeable, logger, ConnectionState.REJECTED)
                        Err(FailureReason("Join rejected."))
                    }
                }
            }
    } catch (e: Exception) {
        val message = "Error while joining: ${e.message}"
        logger.error(message, e)
        closeLink(connection, closeable, logger, ConnectionState.ERROR)
        return Err(FailureReason(message))
    }
    // One of the above returns @err and @ok will be called.
    logger.never("Should not reach here.")
    closeLink(connection, closeable, logger, ConnectionState.ERROR)
    return Err(FailureReason("Join did not conclude."))
}
