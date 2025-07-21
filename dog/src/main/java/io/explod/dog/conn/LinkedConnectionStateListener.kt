package io.explod.dog.conn

/** Callback for when a connection state changes. */
fun interface LinkedConnectionStateListener {
    fun onConnectionStateChanged(connection: LinkedConnection, connectionState: ConnectionState)
}
