package io.explod.dog

import io.explod.dog.conn.Link

/** Listener for connection changes. */
interface ManagedConnectionListener {
    /** A connection was found for the first time. */
    fun onConnectionAdded(connection: ConnectionInformation)

    /** Connection information was updated. If the link was unchanged, the link is null. */
    fun onConnectionUpdated(connection: ConnectionInformation, link: Link?)

    /** Removed for any reason, particularly deduplication. Should not longer be displayed. */
    fun onConnectionRemoved(connection: ConnectionInformation)
}
