package io.explod.dog_app.util

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.explod.loggly.Logger
import java.util.concurrent.atomic.AtomicLong

class StateLogger(
    private val logName: String,
    val logs: MutableState<LogCollection> = mutableStateOf(LogCollection.create()),
) : Logger {

    override fun getName(): String {
        return logName
    }

    override fun debug(message: String) {
        addLog(Level.DEBUG, message)
    }

    override fun debug(message: String, throwable: Throwable?) {
        addLog(Level.DEBUG, message)
    }

    override fun info(message: String) {
        addLog(Level.INFO, message)
    }

    override fun info(message: String, throwable: Throwable?) {
        addLog(Level.INFO, message)
    }

    override fun warn(message: String) {
        addLog(Level.WARN, message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        addLog(Level.WARN, message)
    }

    override fun error(message: String) {
        addLog(Level.ERROR, message)
    }

    override fun error(message: String, throwable: Throwable?) {
        addLog(Level.ERROR, message)
    }

    override fun never(message: String) {
        addLog(Level.NEVER, message)
    }

    override fun never(message: String, throwable: Throwable?) {
        addLog(Level.NEVER, message)
    }

    private fun addLog(level: Level, message: String) {
        logs.value = logs.value.withLog(level, logName, message)
    }

    override fun child(childName: String): Logger {
        return StateLogger(Logger.childName(logName, childName), logs)
    }
}

data class LogCollection(val id: Long, val logs: List<Log>) {

    fun withLog(level: Level, logName: String, message: String): LogCollection {
        val newLog = Log(id = nextLogId(), logName, level, message)
        val newLogs = logs + listOf(newLog)
        return LogCollection(nextId(), newLogs)
    }

    companion object {
        private val nextId = AtomicLong(0L)
        private val nextLogId = AtomicLong(0L)

        fun create(): LogCollection {
            return LogCollection(nextId(), emptyList())
        }

        private fun nextId(): Long {
            return nextId.getAndIncrement()
        }

        private fun nextLogId(): Long {
            return nextLogId.getAndIncrement()
        }
    }
}

data class Log(val id: Long, val logName: String, val level: Level, val message: String)

enum class Level(
    val code: Char,
    /** 3-char code name. */
    val shortCode: String,
) {
    DEBUG(code = 'D', shortCode = "DBG"),
    INFO(code = 'I', shortCode = "NFO"),
    WARN(code = 'W', shortCode = "WRN"),
    ERROR(code = 'E', shortCode = "ERR"),
    NEVER(code = 'A', shortCode = "WTF"),
}
