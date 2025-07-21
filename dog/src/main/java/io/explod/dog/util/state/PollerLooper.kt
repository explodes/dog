package io.explod.dog.util.state

import io.explod.dog.util.Locked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PollerLooper<StateT>(
    private val loopDelay: Long,
    private val jobScope: CoroutineScope,
    private val checker: Checker<StateT>,
) {

    private val job = Locked<Job?>(null)

    protected val mutableFlow = MutableStateFlow(checker.calculateState(lastState = null))
    val flow = mutableFlow.asStateFlow()

    fun start() {
        job.locked {
            if (it == null) {
                val newJob = jobScope.launch { loop() }
                setValue(newJob)
            }
        }
    }

    fun stop() {
        job.locked {
            it?.cancel()
            setValue(null)
        }
    }

    private suspend fun loop() {
        while (true) {
            job()
            delay(loopDelay)
        }
    }

    private fun job() {
        val lastState = mutableFlow.value
        val newState = checker.calculateState(lastState)
        if (lastState != newState) {
            mutableFlow.value = newState
        }
    }

    interface Checker<StateT> {
        /**
         * Calculate the new state. lastState is null if this is the first time state is being
         * calculated, otherwise, it is StateT.
         */
        fun calculateState(lastState: StateT?): StateT
    }
}
