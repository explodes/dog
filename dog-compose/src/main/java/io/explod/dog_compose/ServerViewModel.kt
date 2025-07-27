package io.explod.dog_compose

import androidx.lifecycle.ViewModel
import io.explod.dog.conn.ConnectionState
import io.explod.dog.AdvanceReason
import io.explod.dog.ConnectionInformation
import io.explod.dog.protocol.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal abstract class ServerViewModel : ViewModel() {

    @Suppress("PropertyName") protected val _uiState = MutableStateFlow(Versioned.of(UiState()))
    val uiState: StateFlow<Versioned<UiState>> = _uiState.asStateFlow()

    abstract fun startSearch()

    abstract fun stopSearch()

    protected fun makeDevices(foundServers: Collection<ConnectionInformation>): List<Device> {
        return foundServers.map { server ->
            Device(
                id = server.connection.chainId.id,
                connection = server.connection,
                name = server.name,
                deviceType = server.deviceType ?: DeviceType.PHONE,
                status =
                    when (server.connectionState) {
                        ConnectionState.CONNECTED -> Device.Status.CONNECTED
                        ConnectionState.CLOSED -> Device.Status.DISCONNECTED
                        ConnectionState.REJECTED -> Device.Status.REJECTED
                        ConnectionState.ERROR -> Device.Status.ERROR
                        ConnectionState.RECONNECTING -> Device.Status.CONNECTING
                        ConnectionState.OPENING ->
                            when (server.advance?.advanceReason) {
                                AdvanceReason.BOND -> Device.Status.WAITING_FOR_BOND
                                AdvanceReason.ADMIT -> Device.Status.WAITING_FOR_ADMIT
                                AdvanceReason.JOIN -> Device.Status.WAITING_FOR_JOIN
                                null -> Device.Status.CONNECTING
                            }
                    },
                onClick = server.advance?.advance,
            )
        }
    }

    data class UiState(val devices: List<Device> = emptyList())
}
