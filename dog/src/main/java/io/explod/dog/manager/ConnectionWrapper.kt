package io.explod.dog.manager

import io.explod.dog.conn.Connection

internal class ConnectionWrapper(private val connection: Connection) : Connection by connection {
    override fun toString(): String {
        return "ConnectionWrapper($connection)"
    }
}
