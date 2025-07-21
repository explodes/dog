package io.explod.dog.manager

import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.FullIdentityLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.PartialIdentityLink
import io.explod.dog.conn.advanceInScope
import io.explod.dog.protocol.Protocol
import io.explod.loggly.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class ClientListeners(
    private val scope: CoroutineScope,
    private val ioContext: CoroutineContext,
    private val eagerMode: Boolean,
    private val logger: Logger,
    managedConnectionListener: ManagedConnectionListener,
) : Listeners(managedConnectionListener, logger) {

    // Underlying link was updated.
    override fun onLinkChanged(connection: LinkedConnection, link: Link) {
        when (link) {
            is PartialIdentityLink -> onPartialIdentityLinkChanged(connection, link)
            is FullIdentityLink -> onFullIdentityLinkChanged(connection, link)
            is ConnectedLink -> onConnectedLinkChanged(connection, link)
        }
    }

    private fun onPartialIdentityLinkChanged(
        connection: LinkedConnection,
        link: PartialIdentityLink,
    ) {
        if (eagerMode) {
            clearConnectionAdvance(connection, link)
            scope.launch(ioContext) { link.advanceInScope(logger, allowBonding = false) }
        } else {
            val advance =
                Advance(
                    advanceReason = AdvanceReason.JOIN,
                    advance = {
                        clearConnectionAdvance(connection, link)
                        scope.launch(ioContext) {
                            link.advanceInScope(logger, allowBonding = false)
                        }
                    },
                    reject = {
                        clearConnectionAdvance(connection, link)
                        scope.launch(ioContext) { connection.close() }
                    },
                )
            updateConnectionAdvance(connection, link, advance)
        }
    }

    private fun onFullIdentityLinkChanged(connection: LinkedConnection, link: FullIdentityLink) {
        // Joining the server is completed by the user on the server.
        // We wait here for the connection to to be established.
        clearConnectionAdvance(connection, link)
        scope.launch(ioContext) { link.advanceInScope(Protocol.Join.ACCEPT, logger) }
    }

    private fun onConnectedLinkChanged(connection: LinkedConnection, link: ConnectedLink) {
        clearConnectionAdvance(connection, link)
    }
}
