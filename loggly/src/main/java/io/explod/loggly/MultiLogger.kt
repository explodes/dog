package io.explod.loggly

import java.util.concurrent.ConcurrentHashMap

class MultiLogger
private constructor(private val name: String, initialLoggers: List<Logger>? = null) : Logger {

    private val loggers =
        ConcurrentHashMap<Logger, Boolean>().apply { initialLoggers?.forEach { put(it, true) } }

    fun addLogger(logger: Logger) {
        loggers.put(logger, true)
    }

    fun removeLogger(logger: Logger): Boolean {
        return loggers.remove(logger) == true
    }

    override fun getName(): String {
        return name
    }

    override fun debug(message: String) {
        loggers.forEach { (logger, _) -> logger.debug(message) }
    }

    override fun debug(message: String, throwable: Throwable?) {
        loggers.forEach { (logger, _) -> logger.debug(message, throwable) }
    }

    override fun info(message: String) {
        loggers.forEach { (logger, _) -> logger.info(message) }
    }

    override fun info(message: String, throwable: Throwable?) {
        loggers.forEach { (logger, _) -> logger.info(message, throwable) }
    }

    override fun warn(message: String) {
        loggers.forEach { (logger, _) -> logger.warn(message) }
    }

    override fun warn(message: String, throwable: Throwable?) {
        loggers.forEach { (logger, _) -> logger.warn(message, throwable) }
    }

    override fun error(message: String) {
        loggers.forEach { (logger, _) -> logger.error(message) }
    }

    override fun error(message: String, throwable: Throwable?) {
        loggers.forEach { (logger, _) -> logger.error(message, throwable) }
    }

    override fun never(message: String) {
        loggers.forEach { (logger, _) -> logger.never(message) }
    }

    override fun never(message: String, throwable: Throwable?) {
        loggers.forEach { (logger, _) -> logger.never(message, throwable) }
    }

    override fun child(childName: String): Logger {
        val name = Logger.Companion.childName(name, childName)
        val initialLoggers = loggers.map { (logger, _) -> logger.child(childName) }
        return MultiLogger(name, initialLoggers)
    }

    companion object {
        fun create(name: String, vararg loggers: Logger): MultiLogger {
            return MultiLogger(name, loggers.toList())
        }
    }
}
