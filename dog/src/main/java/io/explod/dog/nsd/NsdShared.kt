package io.explod.dog.nsd

import android.content.Context
import io.explod.dog.common.IOPartialIdentityLink
import io.explod.dog.conn.ChainId
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
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
    override val chainId: ChainId,
    connection: LinkedConnection,
    private val socket: Socket,
    logger: Logger,
    currentPartialIdentity: PartialIdentity,
    currentFullIdentity: FullIdentity,
    applicationContext: Context,
    userInfo: UserInfo,
    protocol: Protocol,
    private val serviceInfo: ServiceInfo,
) :
    IOPartialIdentityLink(
        connection = connection,
        logger = logger,
        currentPartialIdentity = currentPartialIdentity,
        currentFullIdentity = currentFullIdentity,
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
                val fullIdentity = getFullIdentity()
                val partialIdentity = getPartialIdentity()
                val name = fullIdentity.partialIdentity.name ?: partialIdentity.name
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
