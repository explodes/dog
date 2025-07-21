package io.explod.dog_compose

import io.explod.dog.conn.Connection

sealed interface Selection {

    fun interface SingleSelection : Selection {

        fun onSelected(connection: Connection)
    }

    fun interface MultipleSelection : Selection {

        fun onSelected(connections: List<Connection>)
    }
}
