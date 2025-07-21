package io.explod.dog.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RetrySignalConf {
    private val mutableFlow = MutableStateFlow(Retry.READY)
    internal val flow = mutableFlow.asStateFlow()

    enum class Retry {
        READY,
        BACKOFF,
    }
}

class ConnectionPreferenceConf {
    private val mutableFlow = MutableStateFlow(Preference.CLOSED)
    internal val flow = mutableFlow.asStateFlow()

    fun setPreference(preference: Preference) {
        mutableFlow.value = preference
    }

    enum class Preference {
        CLOSED,
        OPEN,
    }
}
