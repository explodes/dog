package io.explod.dog.conn

import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.PartialIdentity

fun <T> Connection.fromIdentity(getter: PartialIdentity.() -> T?): T? {
    return getFullIdentity()?.partialIdentity?.getter() ?: getPartialIdentity()?.getter()
}

internal fun createName(deviceType: DeviceType?, connectionType: ConnectionType?): String {
    return when (deviceType) {
        DeviceType.PHONE ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected Phone"
                ConnectionType.BLUETOOTH -> "Bluetooth Phone"
                null -> "Phone"
            }

        DeviceType.TABLET ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected Tablet"
                ConnectionType.BLUETOOTH -> "Bluetooth Tablet"
                null -> "Tablet"
            }

        DeviceType.TV ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected TV"
                ConnectionType.BLUETOOTH -> "Bluetooth TV"
                null -> "TV"
            }

        DeviceType.CAR ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected Car"
                ConnectionType.BLUETOOTH -> "Bluetooth Car"
                null -> "Car"
            }

        DeviceType.DESKTOP ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected Desktop"
                ConnectionType.BLUETOOTH -> "Bluetooth Desktop"
                null -> "Desktop"
            }

        DeviceType.WATCH ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected Watch"
                ConnectionType.BLUETOOTH -> "Bluetooth Watch"
                null -> "Watch"
            }

        null ->
            when (connectionType) {
                ConnectionType.NSD -> "Connected Device"
                ConnectionType.BLUETOOTH -> "Bluetooth Device"
                null -> "Device"
            }
    }
}
