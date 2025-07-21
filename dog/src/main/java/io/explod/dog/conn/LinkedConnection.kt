@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.explod.dog.conn

import io.explod.loggly.Logger
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.contracts.ExperimentalContracts

/**
 * A connection represents a single connection between two devices. An underlying connection, called
 * the link, is responsible for the actual I/O. The active link may be swapped out for a more
 * preferable link during the server discovery phase.
 */
interface LinkedConnection : Connection {
    override val chainId: ChainId
    val stateListeners: LinkedConnectionStateListenerSet
    val linkListeners: LinkListenerSet

    /** When a link upgrades, it should assign itself to this connection to replace the old link. */
    fun setLink(link: Link, connectionState: ConnectionState)

    fun setConnectionState(connectionState: ConnectionState)

    fun getConnectionState(): ConnectionState

    /** Broadcast a change of Identity data for a link. */
    fun notifyLinkIdentityChanged(link: Link)

    companion object {
        fun create(
            logger: Logger,
            linkedConnectionStateListener: LinkedConnectionStateListener?,
            linkListener: LinkListener?,
            connectionState: ConnectionState = ConnectionState.OPENING,
        ): LinkedConnection {
            val chainId = ChainId.next()
            val connection =
                LinkedConnectionImpl(chainId, logger).apply {
                    linkedConnectionStateListener?.let { stateListeners.add(it) }
                    linkListener?.let { linkListeners.add(it) }
                }
            return connection
        }
    }
}
