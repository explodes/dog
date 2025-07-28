package io.explod.dog.conn

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.explod.dog.conn.ConnectionState.CLOSED
import io.explod.dog.conn.ConnectionState.RECONNECTING
import io.explod.dog.protocol.Identity
import io.explod.dog.testing.TestLogger
import io.explod.dog.testing.isErr
import io.explod.dog.testing.isOk
import io.explod.dog.util.Ok
import io.explod.dog.util.Result.Companion.Ok
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkedConnectionImplTest {

    private val chainId = ChainId.next()
    private lateinit var underTest: LinkedConnectionImpl

    @Before
    fun setUp() {
        underTest = LinkedConnectionImpl(chainId = chainId, logger = TestLogger)
    }

    @Test
    fun send_beforeOpen() = runTest {
        val send = runCatching { withTimeout(1_000) { underTest.send(ByteArray(0)) } }

        assertThat(send.isSuccess).isFalse() // timed out.
    }

    @Test
    fun send_duringOpen() = runTest {
        val send = async { underTest.send(ByteArray(0)) }

        backgroundScope.launch {
            val connectedLink = mockk<ConnectedLink>(relaxed = true)
            coEvery { connectedLink.send(any()) } returns Ok(Ok)
            underTest.setLink(connectedLink, ConnectionState.CONNECTED)
        }

        val result = send.await()
        result.isOk()
    }

    @Test
    fun send_whileOpen() {
        val connectedLink = mockk<ConnectedLink>(relaxed = true)
        coEvery { connectedLink.send(any()) } returns Ok(Ok)
        underTest.setLink(connectedLink, ConnectionState.CONNECTED)

        val result = runBlocking { underTest.send(ByteArray(0)) }

        result.isOk()
    }

    @Test
    fun send_afterClose() {
        underTest.close()

        val result = runBlocking { underTest.send(ByteArray(0)) }

        result.isErr { assertThat(it.message).isEqualTo("Connection is closed.") }
    }

    @Test
    fun receive_beforeOpen() = runTest {
        val send = runCatching { withTimeout(1_000) { underTest.receive(ByteArray(0)) } }

        assertThat(send.isSuccess).isFalse() // timed out.
    }

    @Test
    fun receive_duringOpen() = runTest {
        val receive = async { underTest.receive(ByteArray(0)) }

        backgroundScope.launch {
            val connectedLink = mockk<ConnectedLink>(relaxed = true)
            coEvery { connectedLink.receive(any()) } returns Ok(50)
            underTest.setLink(connectedLink, ConnectionState.CONNECTED)
        }

        val result = receive.await()
        result.isOk()
    }

    @Test
    fun receive_whileOpen() {
        val connectedLink = mockk<ConnectedLink>(relaxed = true)
        coEvery { connectedLink.receive(any()) } returns Ok(50)
        underTest.setLink(connectedLink, ConnectionState.CONNECTED)

        val result = runBlocking { underTest.receive(ByteArray(0)) }

        result.isOk()
    }

    @Test
    fun receive_afterClose() {
        underTest.close()

        val result = runBlocking { underTest.receive(ByteArray(0)) }

        result.isErr { assertThat(it.message).isEqualTo("Connection is closed.") }
    }

    @Test
    fun getIdentity() {
        val identity = Identity(name = "Test")
        val connectedLink = mockk<ConnectedLink>(relaxed = true)
        coEvery { connectedLink.getIdentity() } returns identity
        underTest.setLink(connectedLink, ConnectionState.CONNECTED)

        val result = underTest.getIdentity()

        assertThat(result).isEqualTo(identity)
    }

    @Test
    fun setConnectionState() {
        val listener = mockk<LinkedConnectionStateListener>(relaxed = true)
        underTest.stateListeners.add(listener)

        underTest.setConnectionState(RECONNECTING)

        verify { listener.onConnectionStateChanged(eq(underTest), eq(RECONNECTING)) }
    }

    @Test
    fun notifyLinkIdentityChanged() {
        val listener = mockk<LinkListener>(relaxed = true)
        underTest.linkListeners.add(listener)

        val connectedLink = mockk<ConnectedLink>(relaxed = true)
        underTest.notifyLinkIdentityChanged(connectedLink)

        verify { listener.onLinkIdentityChanged(eq(underTest), eq(connectedLink)) }
    }

    @Test
    fun close() {
        val listener = mockk<LinkedConnectionStateListener>(relaxed = true)
        underTest.stateListeners.add(listener)

        underTest.close()

        verify { listener.onConnectionStateChanged(eq(underTest), eq(CLOSED)) }
    }

    @Test
    fun close_alreadyTerminal() {
        underTest.close()

        val listener = mockk<LinkedConnectionStateListener>(relaxed = true)
        underTest.stateListeners.add(listener)

        underTest.close()

        verify(exactly = 0) { listener.onConnectionStateChanged(any(), any()) }
    }
}
