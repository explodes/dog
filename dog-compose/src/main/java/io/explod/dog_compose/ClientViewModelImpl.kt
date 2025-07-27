@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog_compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.explod.dog.Dog
import io.explod.dog.ConnectionInformation
import io.explod.dog.ManagedDogClient
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.CoroutinePackage
import io.explod.loggly.Logger
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class ClientViewModelImpl(
    private val dog: Dog,
    private val io: CoroutinePackage,
    private val logger: Logger,
    private val userInfo: UserInfo,
    private val eagerMode: Boolean,
) : ClientViewModel() {

    private val managedDogClient by lazy {
        ManagedDogClient(
            dog = dog,
            scope = viewModelScope,
            ioContext = io.context,
            eagerMode = eagerMode,
            logger = logger,
            onConnections = ::onConnections,
        )
    }

    private fun onConnections(connections: List<ConnectionInformation>) {
        setDevicesFromFoundServer(connections)
    }

    override fun startSearch() {
        managedDogClient.findServer(userInfo)
    }

    override fun stopSearch() {
        managedDogClient.stopFindingServer()
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
