package io.explod.dog.nsd

import android.content.Context
import io.explod.dog.common.IOPartialIdentityLink
import io.explod.dog.conn.Link
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

abstract class NsdPartialIdentityLink(
    connection: LinkedConnection,
    private val socket: Socket,
    logger: Logger,
    currentRemoteIdentity: Identity,
    applicationContext: Context,
    userInfo: UserInfo,
    protocol: Protocol,
    private val serviceInfo: ServiceInfo,
) :
    IOPartialIdentityLink(
        connection = connection,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        applicationContext = applicationContext,
        userInfo = userInfo,
        protocol = protocol,
    ) {

    override fun createSocket(): ReaderWriterCloser {
        return object : ReaderWriterCloser {
            override val inputStream: InputStream
                get() = socket.inputStream

            override val outputStream: OutputStream
                get() = socket.outputStream

            override fun close() {
                socket.close()
            }

            override fun toString(): String {
                val remoteIdentity = getIdentity()
                val name = remoteIdentity.name
                val address = "${socket.remoteSocketAddress}:${socket.port}"
                if (name != null) {
                    return "Socket(nsd=${serviceInfo.nsdServiceType},$name)"
                }
                return "Socket(nsd=${serviceInfo.nsdServiceType},$name,$address)"
            }
        }
    }

    override fun isBonded(): Boolean {
        return true
    }

    override suspend fun advanceByBonding(): Result<Link, FailureReason> {
        return Err(FailureReason("Device not allowed to bond."))
    }
}
