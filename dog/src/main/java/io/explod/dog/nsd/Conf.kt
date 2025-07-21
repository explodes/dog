package io.explod.dog.nsd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NsdAvailabilityConf {

    private val mutableFlow = MutableStateFlow(Availability.AVAILABLE)
    internal val flow = mutableFlow.asStateFlow()

    enum class Availability {
        AVAILABLE
    }
}
