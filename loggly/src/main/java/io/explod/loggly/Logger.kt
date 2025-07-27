package io.explod.loggly

interface Logger {
    fun getName(): String

    fun debug(message: String)

    fun debug(message: String, throwable: Throwable? = null)

    fun info(message: String)

    fun info(message: String, throwable: Throwable? = null)

    fun warn(message: String)

    fun warn(message: String, throwable: Throwable? = null)

    fun error(message: String)

    fun error(message: String, throwable: Throwable? = null)

    fun never(message: String)

    fun never(message: String, throwable: Throwable? = null)

    fun child(childName: String): Logger

    companion object {
        fun create(name: String): Logger {
            return LogcatLogger(name)
        }

        fun childName(parentName: String, childName: String): String {
            return "$parentName.$childName"
        }
    }
}

