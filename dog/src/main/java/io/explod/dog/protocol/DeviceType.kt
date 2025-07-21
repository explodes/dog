package io.explod.dog.protocol

enum class DeviceType(val byte: Byte) {
    PHONE(1),
    TABLET(2),
    TV(3),
    CAR(4),
    DESKTOP(5),
    WATCH(6);

    companion object {
        fun fromByte(byte: Byte): DeviceType? {
            return when (byte) {
                1.toByte() -> PHONE
                2.toByte() -> TABLET
                3.toByte() -> TV
                4.toByte() -> CAR
                5.toByte() -> DESKTOP
                6.toByte() -> WATCH
                else -> null
            }
        }
    }
}
