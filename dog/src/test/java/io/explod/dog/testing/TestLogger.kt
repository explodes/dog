package io.explod.dog.testing

import io.explod.loggly.Logger

object TestLogger : Logger {
    override fun getName(): String {
        return "Test"
    }

    override fun debug(message: String) {
        println("DEBUG: $message")
    }

    override fun debug(message: String, throwable: Throwable?) {
        println("DEBUG: $message: t=${throwable?.message}")
    }

    override fun info(message: String) {
        println("INFO: $message")
    }

    override fun info(message: String, throwable: Throwable?) {
        println("INFO: $message: t=${throwable?.message}")
    }

    override fun warn(message: String) {
        println("WARN: $message")
    }

    override fun warn(message: String, throwable: Throwable?) {
        println("WARN: $message: t=${throwable?.message}")
    }

    override fun error(message: String) {
        println("ERROR: $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        println("ERROR: $message: t=${throwable?.message}")
    }

    override fun never(message: String) {
        println("NEVER: $message")
    }

    override fun never(message: String, throwable: Throwable?) {
        println("NEVER: $message: t=${throwable?.message}")
    }

    override fun child(childName: String): Logger {
        return this
    }
}
