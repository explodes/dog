package io.explod.dog.rfcomm

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.os.Build
import io.explod.dog.util.Conf
import io.explod.dog.util.state.BluetoothChecker
import io.explod.dog.util.state.PermissionsChecker
import io.explod.dog.util.state.PollerLooper
import io.explod.dog.util.state.flowForPermissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class BluetoothPermissionsConf(
    permissionsPollerLooper: PollerLooper<Map<String, PermissionsChecker.PermissionStatus>>
) : Conf {
    internal val flow: Flow<PermissionsChecker.PermissionStatus> =
        permissionsPollerLooper.flowForPermissions(permissions = bluetoothPermissions())

    companion object {
        @SuppressLint("ObsoleteSdkInt")
        fun bluetoothPermissions(): Collection<String> {
            val perms =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                } else {
                    listOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                }
            return perms
        }
    }
}

class BleAvailabilityConf(
    bluetoothAdapter: BluetoothAdapter?,
    bluetoothPollerLooper: PollerLooper<BluetoothChecker.BluetoothState>,
) : Conf {
    internal val flow =
        if (
            bluetoothAdapter == null ||
                !bluetoothAdapter.isMultipleAdvertisementSupported ||
                bluetoothAdapter.bluetoothLeAdvertiser == null
        ) {
            flowOf(Availability.NOT_AVAILABLE)
        } else {
            bluetoothPollerLooper.flow.map {
                when (it) {
                    BluetoothChecker.BluetoothState.OFF -> Availability.NOT_AVAILABLE
                    BluetoothChecker.BluetoothState.ON -> Availability.AVAILABLE
                }
            }
        }

    enum class Availability {
        AVAILABLE,
        NOT_AVAILABLE,
    }
}
