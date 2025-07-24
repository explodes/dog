package io.explod.dog.conn

import androidx.annotation.CheckResult
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Ok
import io.explod.dog.util.Result
import io.explod.dog.util.flatMapOk
import io.explod.dog.util.flatOk
import io.explod.dog.util.locked
import io.explod.dog.util.mapOk
import io.explod.loggly.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

internal class LinkedConnectionImpl(override val chainId: ChainId, private val logger: Logger) :
    LinkedConnection {

    override val stateListeners = LinkedConnectionStateListenerSet(this)

    override val linkListeners = LinkListenerSet(logger)

    private val linkState = locked { LinkState() }

    @CheckResult
    override suspend fun send(bytes: ByteArray): Result<Ok, FailureReason> {
        return awaitOpenConnection().flatMapOk { it.send(bytes) }
    }

    @CheckResult
    override suspend fun receive(buffer: ByteArray): Result<Int, FailureReason> {
        return awaitOpenConnection().flatMapOk { it.receive(buffer) }
    }

    private suspend fun awaitOpenConnection(): Result<ConnectedLink, FailureReason> {
        return checkClosed() // Quick check before waiting.
            .mapOk { linkState.locked { it.connectedLinkPromise }.await() }
            .flatOk { _ -> checkClosed() } // Recheck check after waiting.
    }

    override fun getPartialIdentity(): PartialIdentity? {
        return linkState.locked { it.activeLink }?.getPartialIdentity()
    }

    override fun getFullIdentity(): FullIdentity? {
        return linkState.locked { it.activeLink }?.getFullIdentity()
    }

    override fun setLink(link: Link, connectionState: ConnectionState) {
        logger.debug("setLink: link=$link connectionState=$connectionState")
        linkState.locked {
            it.activeLink = link
            setConnectionState(connectionState)
            if (link is ConnectedLink) {
                it.connectedLinkPromise.complete(link)
            }
        }
        linkListeners.notifyLinkChanged(this, link)
    }

    override fun setConnectionState(connectionState: ConnectionState) {
        stateListeners.set(connectionState)
        if (connectionState.isTerminal) {
            close()
        }
    }

    override fun getConnectionState(): ConnectionState {
        return stateListeners.get()
    }

    override fun notifyLinkIdentityChanged(link: Link) {
        linkListeners.notifyLinkIdentityChanged(this, link)
    }

    override fun close() {
        linkState.locked { linkState ->
            if (linkState.closed) {
                // Already closed.
                return@locked
            }
            logger.debug("Closing connection...")
            linkState.connectedLinkPromise.cancel()
            linkState.closed = true
            stateListeners.set(ConnectionState.CLOSED) // Will not override other terminal states.
            linkState.activeLink?.let { runBlocking { it.close() } }
        }
    }

    private fun checkClosed(): Result<Ok, FailureReason> {
        val closed = linkState.locked { it.closed }
        if (closed) {
            return Result.Companion.Err(FailureReason("Connection is closed."))
        }
        return Result.Companion.Ok(Ok)
    }

    override fun toString(): String {
        val (closed, link) =
            linkState.locked {
                val link = it.activeLink
                if (link == null) {
                    // not yet open means not yet closed.
                    false to null
                } else {
                    it.closed to it.activeLink
                }
            }
        return "Connection(chainId=${chainId.id},state=${getConnectionState()},closed=$closed,link=$link)"
    }

    private class LinkState(
        /** True when everything breaks when you try to use this connection. */
        var closed: Boolean = false,
        /** The current active link. */
        var activeLink: Link? = null,
        /** Used to block send and receive until we have a ConnectedLink. */
        val connectedLinkPromise: CompletableDeferred<ConnectedLink> = CompletableDeferred(),
    )
}
