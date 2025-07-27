@file:Suppress("unused")

package io.explod.dog

import io.explod.dog.protocol.DedupNonce
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.UserInfo
import io.explod.loggly.Logger
import io.explod.loggly.NullLogger
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * Manages Dog client connections. Devices changes are reported in a consistent manner.
 */
class ManagedDogServer(
    dog: Dog,
    scope: CoroutineScope,
    ioContext: CoroutineContext,
    logger: Logger = NullLogger,
    onConnections: (List<ConnectionInformation>) -> Unit,
    /** For best results, use a persistent nonce. */
    localNonce: DedupNonce = DedupNonce(Random.nextLong()),
    enableDeduplication: Boolean = true,
) {
    private val managedConnectionListener =
        if (!enableDeduplication) {
            SimpleForwardingManagedConnectionListener(onConnections = onConnections)
        } else {
            DeduplicatingManagedConnectionListener(
                onConnections = onConnections,
                scope = scope,
                ioContext = ioContext,
                logger = logger,
                localNonce = localNonce,
                protocol = Protocol.Server,
            )
        }
    private val serverListeners =
        ServerListeners(
            scope = scope,
            ioContext = ioContext,
            logger = logger,
            managedConnectionListener = managedConnectionListener,
        )
    private val server =
        dog.createServer(
            linkListener = serverListeners,
            linkedConnectionListener = serverListeners,
            linkedConnectionStateListener = serverListeners,
        )

    fun startServer(userInfo: UserInfo) {
        server.startServer(userInfo)
    }

    fun stopServer() {
        server.stopServer()
    }
}
