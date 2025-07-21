package io.explod.dog.conn

import io.explod.loggly.Logger
import java.util.concurrent.ConcurrentHashMap

/** Observable connection state information for a Connection. */
class LinkListenerSet(private val logger: Logger) {

    private val listeners = ConcurrentHashMap<LinkListener, Boolean>()

    fun add(listener: LinkListener) {
        listeners[listener] = true
    }

    fun remove(listener: LinkListener) {
        listeners.remove(listener)
    }

    internal fun notifyLinkChanged(connection: LinkedConnection, link: Link) {
        logger.debug("notifyLinkChanged: connection=$connection")
        for (listener in listeners.keys) {
            listener.onLinkChanged(connection, link)
        }
    }

    internal fun notifyLinkIdentityChanged(connection: LinkedConnection, link: Link) {
        logger.debug("notifyLinkIdentityChanged: connection=$connection")
        for (listener in listeners.keys) {
            listener.onLinkIdentityChanged(connection, link)
        }
    }
}
