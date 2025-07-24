package io.explod.dog.nsd

import android.content.Context
import io.explod.dog.common.RwcUnidentifiedLink
import io.explod.dog.conn.IdentifiedLink
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.protocol.nsdServiceType
import io.explod.dog.util.FailureReason
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Err
import io.explod.loggly.Logger
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

abstract class NsdUnidentifiedLink(
    connection: LinkedConnection,
    private val socket: Socket,
    logger: Logger,
    currentRemoteIdentity: Identity,
    applicationContext: Context,
    userInfo: UserInfo,
    protocol: Protocol,
    private val serviceInfo: ServiceInfo,
) :
    RwcUnidentifiedLink(
        connection = connection,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        applicationContext = applicationContext,
        userInfo = userInfo,
        protocol = protocol,
    ) {

    override fun createReaderWriterCloser(): ReaderWriterCloser {
        return object : ReaderWriterCloser {
            override val inputStream: InputStream
                get() = socket.inputStream

            override val outputStream: OutputStream
                get() = socket.outputStream

            override fun close() {
                socket.close()
            }

            override fun toString(): String {
                val currentRemoteIdentity = getIdentity()
                val name = currentRemoteIdentity.name

                val s = StringBuilder("Socket(")
                s.append(protocol)
                s.append(",nsd=")
                s.append(serviceInfo.nsdServiceType)

                if (name != null) {
                    s.append(',')
                    s.append(name)
                }

                s.append(socket.remoteSocketAddress)
                s.append(':')
                s.append(socket.port)

                s.append(')')

                return s.toString()
            }
        }
    }

    override fun isPaired(): Boolean {
        return true
    }

    override suspend fun advancePairing(): Result<IdentifiedLink, FailureReason> {
        return Err(FailureReason("Device not allowed to pair."))
    }
}
