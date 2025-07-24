package io.explod.dog.manager

import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.IdentifiedLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.UnidentifiedLink
import io.explod.dog.conn.advanceInScope
import io.explod.dog.protocol.Protocol
import io.explod.loggly.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class ServerListeners(
    private val scope: CoroutineScope,
    private val ioContext: CoroutineContext,
    private val logger: Logger,
    managedConnectionListener: ManagedConnectionListener,
) : Listeners(managedConnectionListener, logger) {

    // Underlying link was updated.
    override fun onLinkChanged(connection: LinkedConnection, link: Link) {
        when (link) {
            is UnidentifiedLink -> onPartialIdentityLinkChanged(connection, link)
            is IdentifiedLink -> onFullIdentityLinkChanged(connection, link)
            is ConnectedLink -> onConnectedLinkChanged(connection, link)
        }
    }

    private fun onPartialIdentityLinkChanged(
        connection: LinkedConnection,
        link: UnidentifiedLink,
    ) {
        // If we're already bonded, we don't need user interaction to advance to
        // being fully identified.
        if (link.isPaired()) {
            // Auto-advance.
            clearConnectionAdvance(connection, link)
            scope.launch(ioContext) { link.advanceInScope(logger, allowPairing = false) }
        } else {
            // Otherwise, we wait for the user to manually initiate bonding.
            val advance =
                Advance(
                    advanceReason = AdvanceReason.BOND,
                    advance = {
                        clearConnectionAdvance(connection, link)
                        scope.launch(ioContext) { link.advanceInScope(logger, allowPairing = true) }
                    },
                    reject = null,
                )
            updateConnectionAdvance(connection, link, advance)
        }
    }

    private fun onFullIdentityLinkChanged(connection: LinkedConnection, link: IdentifiedLink) {
        val advance =
            Advance(
                advanceReason = AdvanceReason.ADMIT,
                advance = {
                    clearConnectionAdvance(connection, link)
                    scope.launch(ioContext) { link.advanceInScope(Protocol.Join.ACCEPT, logger) }
                },
                reject = {
                    clearConnectionAdvance(connection, link)
                    scope.launch(ioContext) { link.advanceInScope(Protocol.Join.REJECT, logger) }
                },
            )
        updateConnectionAdvance(connection, link, advance)
    }

    private fun onConnectedLinkChanged(connection: LinkedConnection, link: ConnectedLink) {
        clearConnectionAdvance(connection, link)
    }
}
