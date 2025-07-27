package io.explod.dog

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.Link
import io.explod.dog.util.locked

internal class SimpleForwardingManagedConnectionListener(
    private val onConnections: (List<ConnectionInformation>) -> Unit
) : ManagedConnectionListener {

    private val state = locked { State() }

    override fun onConnectionAdded(connection: ConnectionInformation) {
        state.locked { state -> state.connections[connection.chainId] = connection }
        notifyConnections()
    }

    override fun onConnectionUpdated(connection: ConnectionInformation, link: Link?) {
        state.locked { state -> state.connections[connection.chainId] = connection }
        notifyConnections()
    }

    override fun onConnectionRemoved(connection: ConnectionInformation) {
        state.locked { state -> state.connections.remove(connection.chainId) }
        notifyConnections()
    }

    private fun notifyConnections() {
        onConnections(state.locked { it.connections.values.toList() })
    }

    private data class State(
        val connections: MutableMap<ChainId, ConnectionInformation> = LinkedHashMap()
    )
}
