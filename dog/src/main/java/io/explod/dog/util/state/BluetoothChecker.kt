package io.explod.dog.util.state

import android.bluetooth.BluetoothAdapter

class BluetoothChecker(private val bluetoothAdapter: BluetoothAdapter?) :
    PollerLooper.Checker<BluetoothChecker.BluetoothState> {

    override fun calculateState(lastState: BluetoothState?): BluetoothState {
        return if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            BluetoothState.ON
        } else {
            BluetoothState.OFF
        }
    }

    enum class BluetoothState {
        ON,
        OFF,
    }

    companion object {
        const val LOOP_DELAY = 1_000L
    }
}
