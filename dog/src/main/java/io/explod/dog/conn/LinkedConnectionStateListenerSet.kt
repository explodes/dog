package io.explod.dog.conn

import io.explod.dog.util.locked
import java.util.concurrent.ConcurrentHashMap

/** Observable connection state information for a Connection. */
class LinkedConnectionStateListenerSet(private val connection: LinkedConnection) {

    private val listeners = ConcurrentHashMap<LinkedConnectionStateListener, Boolean>()
    private val connectionState = locked { ConnectionState.OPENING }

    internal fun get(): ConnectionState {
        return connectionState.locked { it }
    }

    /**
     * Set the new state of the connection. If the connection state is already terminal, this change
     * is ignored. If the new value is the same as the old value, this change is ignored.
     */
    fun set(newConnectionState: ConnectionState) {
        val send =
            this.connectionState.locked { oldConnectionState ->
                if (oldConnectionState.isTerminal) {
                    false
                } else if (oldConnectionState == newConnectionState) {
                    false
                } else {
                    setValue(newConnectionState)
                    true
                }
            }
        if (send) {
            for (listener in listeners.keys) {
                listener.onConnectionStateChanged(connection, newConnectionState)
            }
        }
    }

    fun add(listener: LinkedConnectionStateListener) {
        listeners[listener] = true
    }

    fun remove(listener: LinkedConnectionStateListener) {
        listeners.remove(listener)
    }
}
