@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog_compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.explod.dog.Dog
import io.explod.dog.manager.ConnectionInformation
import io.explod.dog.manager.DogServerManager
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.CoroutinePackage
import io.explod.loggly.Logger
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class ServerViewModelImpl(
    private val dog: Dog,
    private val io: CoroutinePackage,
    private val logger: Logger,
    private val userInfo: UserInfo,
) : ServerViewModel() {

    private val dogServerManager by lazy {
        DogServerManager(
            dog = dog,
            scope = viewModelScope,
            ioContext = io.context,
            logger = logger,
            onConnections = ::onConnections,
        )
    }

    private fun onConnections(connections: List<ConnectionInformation>) {
        _uiState.update { version ->
            version.nextVersion(version.value.copy(devices = makeDevices(connections)))
        }
    }

    //    private val dogServer by lazy {
    //        dog.createServer(
    //            linkedConnectionListener = ServerLinkedConnectionListener(),
    //            linkedConnectionStateListener = ServerLinkedConnectionStateListener(),
    //            linkListener = ServerLinkListener(),
    //        )
    //    }
    //
    //    private val clients: Locked<MutableMap<ChainId, ClientInformation>> = locked {
    // LinkedHashMap() }

    override fun startSearch() {
        dogServerManager.startServer(userInfo)
        //        dogServer.startServer(userInfo)
    }

    override fun stopSearch() {
        dogServerManager.stopServer()
        //        dogServer.stopServer()
    }

    override fun onCleared() {
        super.onCleared()
        stopSearch()
    }

    //
    //    private inline fun updateLobby(crossinline update: (clients: MutableMap<ChainId,
    // ClientInformation>) -> Boolean) {
    //        _uiState.update { uiState ->
    //            clients.locked { clients ->
    //                // Update the map in the block, then publish the change as a new version.
    //                if (update(clients)) {
    //                    uiState.copy(
    //                        devices = uiState.devices.nextVersion(makeDevices(clients.values)),
    //                        lobby = uiState.lobby.nextVersion(LobbyInformation(clients.values)),
    //                    )
    //                } else {
    //                    uiState
    //                }
    //            }
    //        }
    //    }
    //
    //    private fun saveClientInformation(
    //        connection: LinkedConnection,
    //        update: ((ClientInformation) -> ClientInformation)? = null,
    //    ): ClientInformation {
    //        val ref = AtomicReference<ClientInformation?>(null)
    //        updateLobby { users ->
    //            val deviceType = connection.fromIdentity { deviceType }
    //            val connectionType = connection.fromIdentity { connectionType }
    //            val name = connection.fromIdentity { name } ?: createName(
    //                deviceType,
    //                connectionType,
    //            )
    //            val existing = users[connection.chainId]
    //            val repopulated = existing?.copy(
    //                name = name,
    //                deviceType = deviceType,
    //                connectionType = connectionType,
    //                connectionState = connection.getConnectionState(),
    //            ) ?: ClientInformation(
    //                connection = connection,
    //                name = name,
    //                deviceType = deviceType,
    //                connectionType = connectionType,
    //                connectionState = connection.getConnectionState(),
    //                advance = null,
    //            )
    //            val updated = update?.invoke(repopulated) ?: repopulated
    //            users[connection.chainId] = updated
    //            ref.store(updated)
    //            existing != updated
    //        }
    //        return ref.load()!!
    //    }
    //
    //    private fun clearButtons(connection: LinkedConnection) {
    //        updateLobby { users ->
    //            val updated = users[connection.chainId]?.copy(advance = null)
    //            if (updated != null) {
    //                users[connection.chainId] = updated
    //            }
    //            true
    //        }
    //    }
    //
    //    private inner class ServerLinkedConnectionListener : LinkedConnectionListener {
    //
    //        override fun onConnection(connection: LinkedConnection) {
    //            saveClientInformation(connection)
    //        }
    //    }
    //
    //    private inner class ServerLinkedConnectionStateListener : LinkedConnectionStateListener {
    //
    //        override fun onConnectionStateChanged(
    //            connection: LinkedConnection,
    //            connectionState: ConnectionState,
    //        ) {
    //            saveClientInformation(connection) { clientInformation ->
    //                if (clientInformation.connectionState.isTerminal) {
    //                    clientInformation.copy(advance = null)
    //                } else {
    //                    clientInformation
    //                }
    //            }
    //        }
    //    }
    //
    //    private inner class ServerLinkListener : LinkListener {
    //
    //        override fun onLinkChanged(
    //            connection: LinkedConnection,
    //            link: Link,
    //        ) {
    //            saveClientInformation(connection) { clientInformation ->
    //                when (link) {
    //                    is PartialIdentityLink -> {
    //                        // If we're already bonded, we don't need user interaction to advance
    // to
    //                        // being fully identified.
    //                        if (link.isBonded()) {
    //                            // Auto-advance.
    //                            viewModelScope.launch(io.context) {
    //                                link.advanceInScope(
    //                                    logger, allowBonding = false
    //                                )
    //                            }
    //                            clientInformation.copy(advance = null)
    //                        } else {
    //                            // Otherwise, we wait for the user to manually initiate bonding.
    //                            clientInformation.copy(
    //                                advance = Advance(
    //                                    advanceReason = AdvanceReason.BOND,
    //                                    advance = {
    //                                        clearButtons(connection)
    //                                        viewModelScope.launch(io.context) {
    //                                            link.advanceInScope(logger, allowBonding = true)
    //                                        }
    //                                    },
    //                                    reject = null,
    //                                )
    //                            )
    //                        }
    //                    }
    //
    //                    is FullIdentityLink -> clientInformation.copy(
    //                        advance = Advance(
    //                            advanceReason = AdvanceReason.ADMIT,
    //                            advance = {
    //                                viewModelScope.launch(io.context) {
    //                                    link.advanceInScope(Join.ACCEPT, logger)
    //                                }
    //                            },
    //                            reject = {
    //                                viewModelScope.launch(io.context) {
    //                                    link.advanceInScope(Join.REJECT, logger)
    //                                }
    //                            },
    //                        )
    //                    )
    //
    //                    is ConnectedLink -> {
    //                        clientInformation.copy(advance = null)
    //                    }
    //                }
    //            }
    //        }
    //
    //        override fun onLinkIdentityChanged(
    //            connection: LinkedConnection,
    //            link: Link,
    //        ) {
    //            saveClientInformation(connection)
    //        }
    //    }

    companion object {
        class Factory(
            private val dog: Dog,
            private val io: CoroutinePackage,
            private val logger: Logger,
            private val userInfo: UserInfo,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return ServerViewModelImpl(dog = dog, io = io, logger = logger, userInfo = userInfo)
                    as T
            }
        }
    }
}
