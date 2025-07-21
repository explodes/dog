package io.explod.dog_compose

import android.bluetooth.BluetoothDevice
import io.explod.loggly.Logger


fun BluetoothDevice.removeBond(logger: Logger) {
    try {
        javaClass.getMethod("removeBond").invoke(this)
    } catch (e: Exception) {
        logger.error("Removing bond has been failed. ${e.message}")
    }
}