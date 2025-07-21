@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog_compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.explod.dog.Dog
import io.explod.dog.manager.ConnectionInformation
import io.explod.dog.manager.DogClientManager
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.CoroutinePackage
import io.explod.loggly.Logger
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class ClientViewModelImpl(
    private val dog: Dog,
    private val io: CoroutinePackage,
    private val logger: Logger,
    private val userInfo: UserInfo,
    private val eagerMode: Boolean,
) : ClientViewModel() {

    private val dogClientManager by lazy {
        DogClientManager(
            dog = dog,
            scope = viewModelScope,
            ioContext = io.context,
            eagerMode = eagerMode,
            logger = logger,
            onConnections = ::onConnections,
        )
    }

    //    private val dogClient by lazy {
    //        dog.createClient(
    //            linkedConnectionListener = ClientLinkedConnectionListener(),
    //            linkedConnectionStateListener = ClientLinkedConnectionStateListener(),
    //            linkListener = ClientLinkListener(),
    //        )
    //    }

    private fun onConnections(connections: List<ConnectionInformation>) {
        _uiState.update { version ->
            version.nextVersion(version.value.copy(devices = makeDevices(connections)))
        }
    }

    //    private val servers: Locked<MutableMap<ChainId, ServerInformation>> = locked {
    //        // Preserve discovery order.
    //        LinkedHashMap()
    //    }
    //
    override fun startSearch() {
        dogClientManager.findServer(userInfo)
        //        dogClient.findServer(userInfo)
    }

    override fun stopSearch() {
        dogClientManager.stopFindingServer()
        //        dogClient.stopFindingServer()
    }

    override fun onCleared() {
        super.onCleared()
        stopSearch()
    }

    //
    //    private inline fun updateServers(crossinline update: (servers: MutableMap<ChainId,
    // ServerInformation>) -> Boolean) {
    //        _uiState.update { uiStateVersion ->
    //            servers.locked { servers ->
    //                val updated = update(servers)
    //                if (!updated) {
    //                    return@locked uiStateVersion
    //                }
    //                uiStateVersion.nextVersion(
    //                    uiStateVersion.value.copy(devices = makeDevices(servers.values)),
    //                )
    //            }
    //        }
    //    }
    //
    //    private fun saveServerInformation(
    //        connection: LinkedConnection,
    //        update: ((ServerInformation) -> ServerInformation)? = null,
    //    ): ServerInformation {
    //        val ref = AtomicReference<ServerInformation?>(null)
    //        updateServers { users ->
    //            val deviceType = connection.fromIdentity { deviceType }
    //            val connectionType = connection.fromIdentity { connectionType }
    //            val name = connection.fromIdentity { name } ?: createName(
    //                deviceType, connectionType
    //            )
    //            val existing = users[connection.chainId]
    //            val repopulated = existing?.copy(
    //                name = name,
    //                deviceType = deviceType,
    //                connectionType = connectionType,
    //                connectionState = connection.getConnectionState(),
    //            ) ?: run {
    //                logger.debug("Saving new server information for connection $connection")
    //                ServerInformation(
    //                    connection = connection,
    //                    name = name,
    //                    deviceType = deviceType,
    //                    connectionType = connectionType,
    //                    connectionState = connection.getConnectionState(),
    //                    advance = null,
    //                )
    //            }
    //            val updated = update?.invoke(repopulated) ?: repopulated
    //            users[connection.chainId] = updated
    //            ref.store(updated)
    //            existing != updated
    //        }
    //        return ref.load()!!
    //    }
    //
    //    private inner class ClientLinkedConnectionListener : LinkedConnectionListener {
    //
    //        override fun onConnection(connection: LinkedConnection) {
    //            saveServerInformation(connection)
    //        }
    //    }
    //
    //    private inner class ClientLinkedConnectionStateListener : LinkedConnectionStateListener {
    //
    //        override fun onConnectionStateChanged(
    //            connection: LinkedConnection,
    //            connectionState: ConnectionState,
    //        ) {
    //            // Update ServerInformation.
    //            saveServerInformation(connection)
    //        }
    //    }
    //
    //    private inner class ClientLinkListener : LinkListener {
    //
    //        override fun onLinkChanged(
    //            connection: LinkedConnection,
    //            link: Link,
    //        ) {
    //            saveServerInformation(connection) { server ->
    //                when (link) {
    //                    is PartialIdentityLink -> {
    //                        if (eagerMode) {
    //                            viewModelScope.launch(io.context) {
    //                                link.advanceInScope(logger, allowBonding = false)
    //                            }
    //                            server.copy(advance = null)
    //                        } else {
    //                            server.copy(
    //                                advance = Advance(
    //                                    advanceReason = AdvanceReason.JOIN,
    //                                    advance = {
    //                                        viewModelScope.launch(io.context) {
    //                                            link.advanceInScope(logger, allowBonding = false)
    //                                        }
    //                                    },
    //                                    reject = {
    //                                        viewModelScope.launch(io.context) {
    //                                            connection.close()
    //                                        }
    //                                    },
    //                                )
    //                            )
    //                        }
    //                    }
    //
    //                    is FullIdentityLink -> {
    //                        // Joining the server is completed by the user on the server.
    //                        // We wait here for the connection to to be established.
    //                        viewModelScope.launch(io.context) {
    //                            link.advanceInScope(Protocol.Join.ACCEPT, logger)
    //                        }
    //                        server.copy(advance = null)
    //                    }
    //
    //                    is ConnectedLink -> {
    //                        // Now connected!
    //                        stopSearch()
    //                        server.copy(advance = null)
    //                    }
    //                }
    //
    //            }
    //        }
    //
    //        override fun onLinkIdentityChanged(
    //            connection: LinkedConnection,
    //            link: Link,
    //        ) {
    //            saveServerInformation(connection)
    //        }
    //    }

    companion object {
        class Factory(
            private val dog: Dog,
            private val io: CoroutinePackage,
            private val logger: Logger,
            private val userInfo: UserInfo,
            private val eagerMode: Boolean,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return ClientViewModelImpl(
                    dog = dog,
                    io = io,
                    logger = logger,
                    userInfo = userInfo,
                    eagerMode = eagerMode,
                )
                    as T
            }
        }
    }
}
