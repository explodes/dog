package io.explod.dog.protocol

enum class ConnectionType(val byte: Byte, val priority: Int) {
    NSD(byte = 1, priority = 100),
    BLUETOOTH(byte = 2, priority = 200);

    companion object {
        fun fromByte(byte: Byte): ConnectionType? {
            return when (byte) {
                1.toByte() -> NSD
                2.toByte() -> BLUETOOTH
                else -> null
            }
        }
    }
}
