package io.explod.dog.conn

/** Interface for when a connection is received. The connection may be negotiating Links. */
fun interface LinkedConnectionListener {
    /**
     * Called when a connection is received. onLinkChanged will not be called on the adjacent
     * LinkListener.
     */
    fun onConnection(connection: LinkedConnection)
}
