package io.explod.dog.manager

import io.explod.dog.conn.ChainId
import io.explod.dog.conn.Connection
import io.explod.dog.conn.ConnectionState
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.Identity

data class ConnectionInformation(
    val chainId: ChainId,
    val name: String,
    val connection: Connection,
    val snapshot: Snapshot,
    val advance: Advance?,
) {
    val deviceType: DeviceType?
        get() = snapshot.identity?.deviceType

    val connectionState: ConnectionState
        get() = snapshot.connectionState
}

data class Snapshot(val connectionState: ConnectionState, val identity: Identity?)

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
