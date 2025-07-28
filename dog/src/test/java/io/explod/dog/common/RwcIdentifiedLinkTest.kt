package io.explod.dog.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol.Join.ACCEPT
import io.explod.dog.protocol.Protocol.Join.REJECT
import io.explod.dog.protocol.UserInfo
import io.explod.dog.testing.FakeProtocol
import io.explod.dog.testing.FakeReaderWriterCloser
import io.explod.dog.testing.isErr
import io.explod.dog.testing.isOk
import io.explod.dog.util.ImmutableBytes
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result.Companion.Ok
import io.explod.loggly.Logger
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RwcIdentifiedLinkTest {
    private lateinit var applicationContext: Context
    private lateinit var userInfo: UserInfo
    private lateinit var currentRemoteIdentity: Identity
    private lateinit var logger: Logger
    private lateinit var connection: LinkedConnection
    private lateinit var socket: ReaderWriterCloser
    private lateinit var protocol: FakeProtocol
    private lateinit var underTest: RwcIdentifiedLink

    @Before
    fun setUp() {
        applicationContext = ApplicationProvider.getApplicationContext()
        userInfo = UserInfo(userName = null, appBytes = null)
        currentRemoteIdentity =
            Identity(
                name = null,
                deviceType = null,
                connectionType = null,
                appBytes = userInfo.appBytes,
            )
        logger = mockk<Logger>(relaxed = true)
        connection = mockk<LinkedConnection>(relaxed = true)
        socket = FakeReaderWriterCloser()
        protocol =
            FakeProtocol(
                remoteIdentity =
                    Identity(
                        name = "Remote Device",
                        deviceType = DeviceType.WATCH,
                        connectionType = ConnectionType.BLUETOOTH,
                        appBytes = ImmutableBytes.create(byteArrayOf(1, 2, 3)),
                    )
            )
        underTest =
            RwcIdentifiedLink(
                connection = connection,
                logger = logger,
                protocol = protocol,
                currentRemoteIdentity = currentRemoteIdentity,
                rwc = socket,
            )
    }

    @Test
    fun advance_accepted() {
        val result = runBlocking { underTest.advance(ACCEPT) }

        result.isOk { @Suppress("USELESS_IS_CHECK") assert(it is ConnectedLink) }
    }

    @Test
    fun advance_rejected() {
        protocol.joinResponse = Ok(REJECT)
        val result = runBlocking { underTest.advance(REJECT) }

        result.isErr()
    }
}
