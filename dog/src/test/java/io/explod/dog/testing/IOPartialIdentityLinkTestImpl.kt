package io.explod.dog.testing

import android.content.Context
import io.explod.dog.common.IOPartialIdentityLink
import io.explod.dog.conn.FullIdentityLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.FailureReason
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result
import io.explod.dog.util.mapOk
import io.explod.loggly.Logger
import io.mockk.mockk

class IOPartialIdentityLinkTestImpl(
    applicationContext: Context,
    connection: LinkedConnection,
    logger: Logger,
    currentPartialIdentity: PartialIdentity,
    currentFullIdentity: FullIdentity,
    protocol: Protocol,
    userInfo: UserInfo,
    private val socket: ReaderWriterCloser,
    private val bonded: Boolean,
) :
    IOPartialIdentityLink(
        applicationContext = applicationContext,
        connection = connection,
        logger = logger,
        currentPartialIdentity = currentPartialIdentity,
        currentFullIdentity = currentFullIdentity,
        protocol = protocol,
        userInfo = userInfo,
    ) {

    override fun isBonded(): Boolean {
        return bonded
    }

    override fun createSocket(): ReaderWriterCloser {
        return socket
    }

    override suspend fun advanceByBonding(): Result<Link, FailureReason> {
        return createFullIdentityLink(socket).mapOk { it as Link }
    }

    override fun createFullIdentityLink(
        socket: ReaderWriterCloser
    ): Result<FullIdentityLink, FailureReason> {
        return Result.Companion.Ok(mockk<FullIdentityLink>())
    }

    class Factory(
        private val applicationContext: Context,
        private val connection: LinkedConnection,
        private val logger: Logger,
        private val currentPartialIdentity: PartialIdentity,
        private val currentFullIdentity: FullIdentity,
        private val userInfo: UserInfo,
        private val socket: ReaderWriterCloser,
        private val protocol: Protocol,
    ) {
        fun create(bonded: Boolean): IOPartialIdentityLinkTestImpl {
            return IOPartialIdentityLinkTestImpl(
                applicationContext = applicationContext,
                connection = connection,
                logger = logger,
                currentPartialIdentity = currentPartialIdentity,
                currentFullIdentity = currentFullIdentity,
                protocol = protocol,
                userInfo = userInfo,
                socket = socket,
                bonded = bonded,
            )
        }
    }
}
