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

internal class ServerListeners(
    private val scope: CoroutineScope,
    private val ioContext: CoroutineContext,
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
        // If we're already bonded, we don't need user interaction to advance to
        // being fully identified.
        if (link.isBonded()) {
            // Auto-advance.
            clearConnectionAdvance(connection, link)
            scope.launch(ioContext) { link.advanceInScope(logger, allowBonding = false) }
        } else {
            // Otherwise, we wait for the user to manually initiate bonding.
            val advance =
                Advance(
                    advanceReason = AdvanceReason.BOND,
                    advance = {
                        clearConnectionAdvance(connection, link)
                        scope.launch(ioContext) { link.advanceInScope(logger, allowBonding = true) }
                    },
                    reject = null,
                )
            updateConnectionAdvance(connection, link, advance)
        }
    }

    private fun onFullIdentityLinkChanged(connection: LinkedConnection, link: FullIdentityLink) {
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
