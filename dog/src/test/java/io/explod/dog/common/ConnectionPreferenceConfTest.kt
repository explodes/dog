package io.explod.dog.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectionPreferenceConfTest {

    @Test
    fun setPreference() {
        val underTest = ConnectionPreferenceConf()
        assertThat(underTest.flow.value).isEqualTo(ConnectionPreferenceConf.Preference.CLOSED)

        underTest.setPreference(ConnectionPreferenceConf.Preference.OPEN)
        assertThat(underTest.flow.value).isEqualTo(ConnectionPreferenceConf.Preference.OPEN)
    }
}
