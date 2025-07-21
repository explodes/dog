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

    override fun startSearch() {
        dogServerManager.startServer(userInfo)
    }

    override fun stopSearch() {
        dogServerManager.stopServer()
    }

    override fun onCleared() {
        super.onCleared()
        stopSearch()
    }

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
