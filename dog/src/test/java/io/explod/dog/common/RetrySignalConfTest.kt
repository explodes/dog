package io.explod.dog.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetrySignalConfTest {

    @Test
    fun setPreference() {
        val underTest = RetrySignalConf()
        assertThat(underTest.flow.value).isEqualTo(RetrySignalConf.Retry.READY)

        underTest.setPreference(RetrySignalConf.Retry.BACKOFF)
        assertThat(underTest.flow.value).isEqualTo(RetrySignalConf.Retry.BACKOFF)
    }
}
