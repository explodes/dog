package io.explod.dog.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.explod.dog.conn.FullIdentityLink
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.protocol.UserInfo
import io.explod.dog.testing.FakeProtocol
import io.explod.dog.testing.FakeReaderWriterCloser
import io.explod.dog.testing.IOPartialIdentityLinkTestImpl
import io.explod.dog.util.ImmutableBytes
import io.explod.dog.util.ReaderWriterCloser
import io.explod.loggly.Logger
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IOPartialIdentityLinkTest {
    private lateinit var applicationContext: Context
    private lateinit var userInfo: UserInfo
    private lateinit var currentFullIdentity: FullIdentity
    private lateinit var logger: Logger
    private lateinit var connection: LinkedConnection
    private lateinit var socket: ReaderWriterCloser
    private lateinit var protocol: FakeProtocol
    private lateinit var factory: IOPartialIdentityLinkTestImpl.Factory

    @Before
    fun setUp() {
        applicationContext = ApplicationProvider.getApplicationContext()
        userInfo = UserInfo(userName = null, appBytes = null)
        currentFullIdentity =
            FullIdentity(
                partialIdentity =
                    PartialIdentity(name = null, deviceType = null, connectionType = null),
                appBytes = userInfo.appBytes,
            )
        logger = mockk<Logger>(relaxed = true)
        connection = mockk<LinkedConnection>(relaxed = true)
        socket = FakeReaderWriterCloser()
        protocol =
            FakeProtocol(
                remoteIdentity =
                    FullIdentity(
                        partialIdentity =
                            PartialIdentity(
                                name = "Remote Device",
                                deviceType = DeviceType.WATCH,
                                connectionType = ConnectionType.BLUETOOTH,
                            ),
                        appBytes = ImmutableBytes.create(byteArrayOf(1, 2, 3)),
                    )
            )
        factory =
            IOPartialIdentityLinkTestImpl.Factory(
                applicationContext = applicationContext,
                connection = connection,
                logger = logger,
                protocol = protocol,
                currentPartialIdentity = currentFullIdentity.partialIdentity,
                currentFullIdentity = currentFullIdentity,
                userInfo = userInfo,
                socket = socket,
            )
    }

    @Test
    fun bonded_advances_client() {
        val underTest = factory.create(bonded = true)

        val result = runBlocking { underTest.advance(false) }

        result
            .err { reason -> fail("Advance failed: $reason") }
            .ok { assert(it is FullIdentityLink) }
    }

    @Test
    fun bondingDisallowed_wontAdvance() {
        val underTest = factory.create(bonded = false)

        val result = runBlocking { underTest.advance(false) }

        result.ok { fail("Advance succeeded unexpectedly: $it") }
    }

    @Test
    fun bondingAllowed_advances() {
        val underTest = factory.create(bonded = false)

        val result = runBlocking { underTest.advance(true) }

        result
            .err { reason -> fail("Advance failed: $reason") }
            .ok { assert(it is FullIdentityLink) }
    }
}
