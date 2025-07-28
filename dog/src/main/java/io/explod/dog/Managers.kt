package io.explod.dog

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.Connection
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.IdentifiedLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.conn.UnidentifiedLink
import io.explod.dog.conn.advanceInScope
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol
import io.explod.dog.util.locked
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

    private fun onPartialIdentityLinkChanged(connection: LinkedConnection, link: UnidentifiedLink) {
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

data class ConnectionInformation(
    val chainId: ChainId,
    val name: String,
    val connection: Connection,
    val snapshot: Snapshot,
    val advance: Advance?,
) {
    val deviceType: DeviceType?
        get() = snapshot.identity?.deviceType

    val connectionState: ConnectionState
        get() = snapshot.connectionState
}

data class Snapshot(val connectionState: ConnectionState, val identity: Identity?)

data class Advance(
    val advanceReason: AdvanceReason,
    val advance: () -> Unit,
    val reject: (() -> Unit)?,
)

enum class AdvanceReason {
    BOND,
    ADMIT,
    JOIN,
}

/** Listener for connection changes. */
interface ManagedConnectionListener {
    /** A connection was found for the first time. */
    fun onConnectionAdded(connection: ConnectionInformation)

    /** Connection information was updated. If the link was unchanged, the link is null. */
    fun onConnectionUpdated(connection: ConnectionInformation, link: Link?)

    /** Removed for any reason, particularly deduplication. Should not longer be displayed. */
    fun onConnectionRemoved(connection: ConnectionInformation)
}

internal abstract class Listeners(
    private val managedConnectionListener: ManagedConnectionListener,
    private val logger: Logger,
) : LinkedConnectionListener, LinkedConnectionStateListener, LinkListener {

    private val connections = locked { LinkedHashMap<ChainId, ConnectionInformation>() }

    private fun addConnection(connection: LinkedConnection) {
        val connectionInformation =
            connections.locked {
                val connectionInformation = connection.asConnectionInformation()
                it[connection.chainId] = connectionInformation
                connectionInformation
            }
        managedConnectionListener.onConnectionAdded(connectionInformation)
    }

    private fun updateConnection(connection: LinkedConnection, link: Link?) {
        updateConnectionInternal(connection, link) { oldValue ->
            if (connection.getConnectionState().isTerminal) {
                oldValue.copy(advance = null, snapshot = connection.getSnapshot())
            } else {
                oldValue.copy(snapshot = connection.getSnapshot())
            }
        }
    }

    protected fun updateConnectionAdvance(
        connection: LinkedConnection,
        link: Link,
        advance: Advance?,
    ) {
        updateConnectionInternal(connection, link) { oldValue ->
            if (connection.getConnectionState().isTerminal) {
                oldValue.copy(advance = null, snapshot = connection.getSnapshot())
            } else {
                oldValue.copy(advance = advance, snapshot = connection.getSnapshot())
            }
        }
    }

    protected fun clearConnectionAdvance(connection: LinkedConnection, link: Link) {
        updateConnectionAdvance(connection, link, advance = null)
    }

    private inline fun updateConnectionInternal(
        connection: LinkedConnection,
        link: Link?,
        crossinline update: (ConnectionInformation) -> ConnectionInformation,
    ) {
        val newConnectionInformation =
            connections.locked {
                val oldValue = it[connection.chainId] ?: return@locked null
                val newValue = update(oldValue)
                it[connection.chainId] = newValue
                newValue
            }
        if (newConnectionInformation != null) {
            managedConnectionListener.onConnectionUpdated(newConnectionInformation, link)
        }
    }

    // New connection received.
    override fun onConnection(connection: LinkedConnection) {
        addConnection(connection)
    }

    // Connection was updated.
    override fun onConnectionStateChanged(
        connection: LinkedConnection,
        connectionState: ConnectionState,
    ) {
        updateConnection(connection, link = null)
    }

    // Identity was updated.
    override fun onLinkIdentityChanged(connection: LinkedConnection, link: Link) {
        updateConnection(connection, link)
    }

    companion object {

        private fun LinkedConnection.asConnectionInformation(): ConnectionInformation {
            return ConnectionInformation(
                chainId = chainId,
                connection = ConnectionWrapper(this),
                snapshot = getSnapshot(),
                advance = null,
                name = name,
            )
        }

        private fun LinkedConnection.getSnapshot(): Snapshot {
            return Snapshot(connectionState = getConnectionState(), identity = getIdentity())
        }
    }
}

internal class ConnectionWrapper(private val connection: Connection) : Connection by connection {
    override fun toString(): String {
        return "ConnectionWrapper($connection)"
    }
}

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
            is UnidentifiedLink -> onPartialIdentityLinkChanged(connection, link)
            is IdentifiedLink -> onFullIdentityLinkChanged(connection, link)
            is ConnectedLink -> onConnectedLinkChanged(connection, link)
        }
    }

    private fun onPartialIdentityLinkChanged(connection: LinkedConnection, link: UnidentifiedLink) {
        if (eagerMode) {
            clearConnectionAdvance(connection, link)
            scope.launch(ioContext) { link.advanceInScope(logger, allowPairing = false) }
        } else {
            val advance =
                Advance(
                    advanceReason = AdvanceReason.JOIN,
                    advance = {
                        clearConnectionAdvance(connection, link)
                        scope.launch(ioContext) {
                            link.advanceInScope(logger, allowPairing = false)
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

    private fun onFullIdentityLinkChanged(connection: LinkedConnection, link: IdentifiedLink) {
        // Joining the server is completed by the user on the server.
        // We wait here for the connection to to be established.
        clearConnectionAdvance(connection, link)
        scope.launch(ioContext) { link.advanceInScope(Protocol.Join.ACCEPT, logger) }
    }

    private fun onConnectedLinkChanged(connection: LinkedConnection, link: ConnectedLink) {
        clearConnectionAdvance(connection, link)
    }
}
