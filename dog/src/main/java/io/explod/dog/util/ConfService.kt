package io.explod.dog.util

import io.explod.loggly.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

abstract class ConfService(private val logger: Logger, private val coro: CoroutinePackage) {
    private var jobs = locked { ServiceJobs() }

    /** Listen to Conf changes and start the service according to Conf. */
    fun observeConf() {
        jobs.locked { jobs ->
            if (jobs.observeJob == null) {
                val observeJob =
                    coro.scope.launch {
                        getConfFlow().distinctUntilChanged().collect(::toggleServiceEnabled)
                    }
                setValue(jobs.copy(observeJob = observeJob))
            }
        }
    }

    /** Stop listening to Conf changes and stop the service. */
    fun stopObservingConf() {
        jobs.locked { jobs ->
            jobs.serviceJob?.cancel()
            jobs.observeJob?.cancel()
            setValue(jobs.copy(observeJob = null, serviceJob = null))
        }
    }

    /** Switch cancel the running service job, or start it up, based on Conf. */
    private fun toggleServiceEnabled(shouldRun: Boolean) {
        logger.debug("toggleServiceEnabled")
        jobs.locked { jobs ->
            val isRunning = jobs.serviceJob != null
            if (isRunning != shouldRun) {
                if (shouldRun) {
                    logger.debug("Service starting... (isRunning=false/shouldRun=true)")
                    val serviceJob = coro.scope.launch { runService() }
                    setValue(jobs.copy(serviceJob = serviceJob))
                } else {
                    logger.debug("Service stopping... (isRunning=true/shouldRun=false)")
                    jobs.serviceJob?.cancel()
                    setValue(jobs.copy(serviceJob = null))
                }
            }
        }
    }

    /** Get a Flow that emits true when the service should run. */
    protected abstract fun getConfFlow(): Flow<Boolean>

    /**
     * Run your service until cancelled. At cancelling, all resources should be closed and freed.
     */
    protected abstract suspend fun runService()

    /** Lockable object for internal job state. */
    private data class ServiceJobs(
        /** Job used to observe Conf changes. */
        val observeJob: Job? = null,
        /** Job used to run the service. */
        val serviceJob: Job? = null,
    )
}
