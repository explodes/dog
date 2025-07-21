package io.explod.loggly

import com.google.common.truth.Truth.assertThat
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class MultiLoggerTest {

    @Test
    fun name() {
        val underTest = MultiLogger.create("test")

        assertThat(underTest.getName()).isEqualTo("test")
    }

    @Test
    fun child() {
        val parent = MultiLogger.create("parent")
        val child = parent.child("child")

        assertThat(child.getName()).isEqualTo("parent.child")
    }

    @Test
    fun delegatesLoggers() {

        val throwable = Exception("throwable")

        val initialLogger = mockk<Logger>(relaxed = true)
        val addedLogger = mockk<Logger>(relaxed = true)
        val removedLogger = mockk<Logger>(relaxed = true)

        val underTest = MultiLogger.create("test", initialLogger)

        underTest.addLogger(addedLogger)
        underTest.addLogger(removedLogger)
        underTest.removeLogger(removedLogger)

        underTest.debug("log")
        underTest.debug("log", throwable)
        underTest.info("log")
        underTest.info("log", throwable)
        underTest.warn("log")
        underTest.warn("log", throwable)
        underTest.error("log")
        underTest.error("log", throwable)
        underTest.never("log")
        underTest.never("log", throwable)

        verifyOrder {
            initialLogger.debug(eq("log"))
            initialLogger.debug(eq("log"), eq(throwable))
            initialLogger.info(eq("log"))
            initialLogger.info(eq("log"), eq(throwable))
            initialLogger.warn(eq("log"))
            initialLogger.warn(eq("log"), eq(throwable))
            initialLogger.error(eq("log"))
            initialLogger.error(eq("log"), eq(throwable))
            initialLogger.never(eq("log"))
            initialLogger.never(eq("log"), eq(throwable))
        }

        verifyOrder {
            addedLogger.debug(eq("log"))
            addedLogger.debug(eq("log"), eq(throwable))
            addedLogger.info(eq("log"))
            addedLogger.info(eq("log"), eq(throwable))
            addedLogger.warn(eq("log"))
            addedLogger.warn(eq("log"), eq(throwable))
            addedLogger.error(eq("log"))
            addedLogger.error(eq("log"), eq(throwable))
            addedLogger.never(eq("log"))
            addedLogger.never(eq("log"), eq(throwable))
        }

        verify {
            initialLogger.hashCode()
            addedLogger.hashCode()
            removedLogger.hashCode()
        }

        confirmVerified(initialLogger, addedLogger, removedLogger)
    }
}
