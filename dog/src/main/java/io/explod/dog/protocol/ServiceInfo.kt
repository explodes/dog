package io.explod.dog.protocol

import io.explod.dog.util.truncateUtf8Bytes
import java.util.UUID

/** Data about your service that will be broadcast to establish connections. */
class ServiceInfo
private constructor(
    val friendlyName: String,
    val systemName: String,
    val uuid: UUID,
    val uuidString: String,
) {
    companion object {
        fun create(
            /** Limit to <= 256 bytes. */
            friendlyName: String,
            /** Limit to lowercase no a-z <= 32 bytes. */
            systemName: String,
            uuidString: String,
        ): ServiceInfo {
            val uuid = UUID.fromString(uuidString)
            val uuidString = uuid.toString() // fix inputs that do not pad their 0's
            return ServiceInfo(
                friendlyName = friendlyName,
                systemName = systemName,
                uuid = UUID.fromString(uuidString),
                uuidString = uuidString,
            )
        }
    }
}

/** Trims `friendlyName` to 256 bytes. */
val ServiceInfo.nsdServiceName: String
    get() = friendlyName.truncateUtf8Bytes(maxBytes = 256)

val ServiceInfo.nsdServiceType: String
    get() {
        val sanitized = systemName.replace(" ", "")
        return "_${sanitized}._tcp"
    }
