package io.explod.dog.conn

/**
 * Possible state of a Connection. Note that Links may be in any state while the Connection is
 * OPENING.
 */
enum class ConnectionState(val isTerminal: Boolean) {
    OPENING(isTerminal = false),
    CONNECTED(isTerminal = false),
    RECONNECTING(isTerminal = false),
    CLOSED(isTerminal = true),
    REJECTED(isTerminal = true),
    ERROR(isTerminal = true),
}
