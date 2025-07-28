package io.explod.dog.testing

import android.content.Context
import io.explod.dog.common.RwcUnidentifiedLink
import io.explod.dog.conn.IdentifiedLink
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.FailureReason
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result
import io.explod.loggly.Logger
import io.mockk.mockk

class FakeRwcUnidentifiedLink(
    applicationContext: Context,
    connection: LinkedConnection,
    logger: Logger,
    currentRemoteIdentity: Identity,
    protocol: Protocol,
    userInfo: UserInfo,
    private val socket: ReaderWriterCloser,
    private val bonded: Boolean,
) :
    RwcUnidentifiedLink(
        applicationContext = applicationContext,
        connection = connection,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        protocol = protocol,
        userInfo = userInfo,
    ) {

    override fun isPaired(): Boolean {
        return bonded
    }

    override fun createReaderWriterCloser(): ReaderWriterCloser {
        return socket
    }

    override suspend fun advancePairing(): Result<IdentifiedLink, FailureReason> {
        // skip identity protocol by not calling advancePaired()
        return Result.Companion.Ok(mockk<IdentifiedLink>())
    }

    class Factory(
        private val applicationContext: Context,
        private val connection: LinkedConnection,
        private val logger: Logger,
        private val currentRemoteIdentity: Identity,
        private val userInfo: UserInfo,
        private val socket: ReaderWriterCloser,
        private val protocol: Protocol,
    ) {
        fun create(bonded: Boolean): FakeRwcUnidentifiedLink {
            return FakeRwcUnidentifiedLink(
                applicationContext = applicationContext,
                connection = connection,
                logger = logger,
                currentRemoteIdentity = currentRemoteIdentity,
                protocol = protocol,
                userInfo = userInfo,
                socket = socket,
                bonded = bonded,
            )
        }
    }
}
