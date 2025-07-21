package io.explod.dog.util

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission

val BluetoothDevice.deviceString: String
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    get() {
        val sb = StringBuilder("BluetoothDevice(")
        if (!name.isNullOrBlank()) {
            sb.append("name='$name',")
        }
        sb.append("address='$address'")
        if (bondState == BluetoothDevice.BOND_BONDED) {
            sb.append(",bond")
        }
        sb.append(')')
        return sb.toString()
    }

val BluetoothDevice.safeName: String
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    get() =
        try {
            name ?: "null"
        } catch (_: SecurityException) {
            "unnamed-device"
        }
