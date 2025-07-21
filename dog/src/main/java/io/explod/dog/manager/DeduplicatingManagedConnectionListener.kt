package io.explod.dog.manager

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.ConnectionState.CLOSED
import io.explod.dog.conn.FullIdentityLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.PartialIdentityLink
import io.explod.dog.protocol.DedupNonce
import io.explod.dog.protocol.Protocol
import io.explod.dog.util.locked
import io.explod.loggly.Logger
import io.explod.loggly.NullLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

internal class DeduplicatingManagedConnectionListener(
    private val scope: CoroutineScope,
    private val ioContext: CoroutineContext,
    private val logger: Logger = NullLogger,
    /** For best results, use a persistent nonce. */
    private val localNonce: DedupNonce = DedupNonce(Random.nextLong()),
    private val onConnections: (List<ConnectionInformation>) -> Unit,
    private val protocol: Protocol,
) : ManagedConnectionListener {

    private val state = locked { State() }

    override fun onConnectionAdded(connection: ConnectionInformation) {
        state.locked { state -> state.connections[connection.chainId] = connection }
        notifyConnections()
    }

    override fun onConnectionUpdated(connection: ConnectionInformation, link: Link?) {
        state.locked { state ->
            state.connections[connection.chainId] = connection
            if (link != null) {
                state.links[connection.chainId] = link
            }
        }
        advanceUnbondedLinks(connection, link)
        notifyConnections()
    }

    private fun advanceUnbondedLinks(connection: ConnectionInformation, link: Link?) {
        if (link == null) {
            return
        }
        state.locked { state -> state.links[connection.chainId] = link }
        fun clearAdvance() {
            state.locked { state ->
                val existing = state.connections[connection.chainId]
                if (existing != null) {
                    state.connections[connection.chainId] = existing.copy(advance = null)
                }
            }
        }
        scope.launch(ioContext) {
            when (link) {
                is PartialIdentityLink -> {
                    // Advance!! (Unless we need to bond :( )
                    if (link.isBonded() && connection.advance != null) {
                        connection.advance.advance()
                        clearAdvance()
                    }
                }

                is FullIdentityLink -> {
                    // Advance!!
                    if (connection.advance != null) {
                        connection.advance.advance()
                        clearAdvance()
                    }
                }

                is ConnectedLink -> {
                    protocol
                        .shareDeduplicationIdentity(connection.connection, logger, localNonce)
                        .err { err ->
                            logger.error("Unable to validate nonce for connection: $err")
                            connection.connection.close()
                            clearAdvance()
                        }
                        .ok { remoteNonce -> exchangeMatchingNonces(remoteNonce, connection, link) }
                }
            }
        }
        notifyConnections()
    }

    private fun exchangeMatchingNonces(
        remoteNonce: DedupNonce,
        newConnection: ConnectionInformation,
        newLink: ConnectedLink,
    ) {
        state.locked { state ->
            val currentConnectionChainId = state.connectedNonces[remoteNonce]
            if (currentConnectionChainId == null) {
                // Nonce was not known. Save it! If we find a duplicate, we hit the next branch.
                logger.debug("New remote nonce: $remoteNonce")
                state.connectedNonces[remoteNonce] = newConnection.chainId
                return@locked
            }

            // Save the best of the two links.
            // Close the other link.
            val currentConnection = state.connections[currentConnectionChainId]
            if (currentConnection == null) {
                logger.never("Unexpected null connection for know nonce.")
                return@locked
            }
            val currentLink = state.links[currentConnectionChainId] as? ConnectedLink
            if (currentLink == null) {
                // Should not happen because the connection has been established, thus a link update
                // received.
                logger.never("Unexpected null connection for know nonce.")
                return@locked
            }

            if (isCurrentBetterThanNew(currentLink, newLink)) {
                logger.info(
                    "Duplicate remote nonce: $remoteNonce! Closing new connection: $newConnection in favor of current connection: $currentConnection"
                )
                newConnection.connection.close()
            } else {
                logger.info(
                    "Duplicate remote nonce: $remoteNonce! Closing current connection: $currentConnection in favor of new connection: $newConnection"
                )
                currentConnection.connection.close()
            }
        }
        notifyConnections()
    }

    private fun isCurrentBetterThanNew(
        currentLink: ConnectedLink,
        newLink: ConnectedLink,
    ): Boolean {
        val currentType = currentLink.getFullIdentity().partialIdentity.connectionType
        val newType = newLink.getFullIdentity().partialIdentity.connectionType

        // If we don't know exactly what we're connecting to,
        // then we stay loyal to our current connection.
        if (newType == null) {
            logger.never("Incoming connected identity was never filled.")
            return true
        }
        // If we don't know exactly what we are,
        // then we trust in the safety of others.
        if (currentType == null) {
            logger.never("Current connected identity was never filled.")
            return false
        }

        // We've been outplayed. We must yield.
        if (newType.priority > currentType.priority) {
            return false
        }

        // We have survived the gauntlet.
        return true
    }

    override fun onConnectionRemoved(connection: ConnectionInformation) {
        state.locked { state -> state.connections.remove(connection.chainId) }
        notifyConnections()
    }

    private fun notifyConnections() {
        onConnections(state.locked { it.connections.values.mapNotNull(::mapConnection) })
    }

    private fun mapConnection(connection: ConnectionInformation): ConnectionInformation? {
        if (connection.connectionState == CLOSED) {
            // Remove the closed duplicates.
            return null
        }

        // We keep everything, even advances, which at this point are ONLY bonding requests!
        return ConnectionInformation(
            chainId = connection.chainId,
            connection = connection.connection,
            snapshot = connection.snapshot,
            advance = connection.advance,
            name = connection.name,
        )
    }

    private data class State(
        // Return order of encounter by using LinkedHashMap.
        val connections: MutableMap<ChainId, ConnectionInformation> = LinkedHashMap(),
        val links: MutableMap<ChainId, Link> = mutableMapOf(),
        val connectedNonces: MutableMap<DedupNonce, ChainId> = mutableMapOf(),
    )
}
