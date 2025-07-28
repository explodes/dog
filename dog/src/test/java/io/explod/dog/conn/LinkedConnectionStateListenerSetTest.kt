package io.explod.dog.conn

import com.google.common.truth.Truth.assertThat
import io.explod.dog.testing.TestLogger
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LinkedConnectionStateListenerSetTest {

    private lateinit var linkedConnection: LinkedConnection
    private lateinit var underTest: LinkedConnectionStateListenerSet

    @Before
    fun setUp() {
        linkedConnection = LinkedConnectionImpl(chainId = ChainId.next(), logger = TestLogger)
        underTest = LinkedConnectionStateListenerSet(linkedConnection)
    }

    @Test
    fun set_get() {
        val initial = underTest.get()
        assertThat(initial).isEqualTo(ConnectionState.OPENING)

        underTest.set(ConnectionState.CONNECTED)
        val connected = underTest.get()

        assertThat(connected).isEqualTo(ConnectionState.CONNECTED)
    }

    @Test
    fun add() {
        val listener = mockk<LinkedConnectionStateListener>()
        every { listener.onConnectionStateChanged(any(), any()) } just runs

        underTest.add(listener)

        underTest.set(ConnectionState.CONNECTED)
        verify {
            listener.onConnectionStateChanged(eq(linkedConnection), eq(ConnectionState.CONNECTED))
        }
    }

    @Test
    fun remove() {
        val listener = mockk<LinkedConnectionStateListener>()

        underTest.add(listener)
        underTest.remove(listener)

        underTest.set(ConnectionState.CONNECTED)
        verify(exactly = 0) {
            listener.onConnectionStateChanged(eq(linkedConnection), eq(ConnectionState.CONNECTED))
        }
    }
}
