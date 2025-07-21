package io.explod.dog.manager

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.Connection
import io.explod.dog.conn.ConnectionState
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity

data class ConnectionInformation(
    val chainId: ChainId,
    val name: String,
    val connection: Connection,
    val snapshot: Snapshot,
    val advance: Advance?,
) {
    val deviceType: DeviceType?
        get() =
            snapshot.fullIdentity?.partialIdentity?.deviceType
                ?: snapshot.partialIdentity?.deviceType

    val connectionState: ConnectionState
        get() = snapshot.connectionState
}

data class Snapshot(
    val connectionState: ConnectionState,
    val partialIdentity: PartialIdentity?,
    val fullIdentity: FullIdentity?,
)

data class Advance(
    val advanceReason: AdvanceReason,
    val advance: () -> Unit,
    val reject: (() -> Unit)?,
)

enum class AdvanceReason {
    BOND,
    ADMIT,
    JOIN,
}
