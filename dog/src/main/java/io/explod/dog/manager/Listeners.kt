package io.explod.dog.manager

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.util.locked
import io.explod.loggly.Logger

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
            return Snapshot(
                connectionState = getConnectionState(),
                partialIdentity = getPartialIdentity(),
                fullIdentity = getFullIdentity(),
            )
        }
    }
}
