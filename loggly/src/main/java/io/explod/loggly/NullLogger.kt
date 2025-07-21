package io.explod.loggly

object NullLogger : Logger {
    override fun getName(): String = "NullLogger"

    override fun debug(message: String) = Unit

    override fun debug(message: String, throwable: Throwable?) = Unit

    override fun info(message: String) = Unit

    override fun info(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit

    override fun error(message: String) = Unit

    override fun error(message: String, throwable: Throwable?) = Unit

    override fun never(message: String) = Unit

    override fun never(message: String, throwable: Throwable?) = Unit

    override fun child(childName: String): Logger = this
}
