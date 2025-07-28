package io.explod.loggly

import android.util.Log
import io.explod.loggly.Logger.Companion.childName

internal class LogcatLogger(private val name: String) : Logger {

    override fun getName(): String {
        return name
    }

    override fun debug(message: String) {
        Log.d(TAG, "[$name] $message")
    }

    override fun debug(message: String, throwable: Throwable?) {
        Log.d(TAG, "[$name] $message", throwable)
    }

    override fun info(message: String) {
        Log.i(TAG, "[$name] $message")
    }

    override fun info(message: String, throwable: Throwable?) {
        Log.i(TAG, "[$name] $message", throwable)
    }

    override fun warn(message: String) {
        Log.w(TAG, "[$name] $message")
    }

    override fun warn(message: String, throwable: Throwable?) {
        Log.w(TAG, "[$name] $message", throwable)
    }

    override fun error(message: String) {
        Log.e(TAG, "[$name] $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        Log.e(TAG, "[$name] $message", throwable)
    }

    override fun never(message: String) {
        Log.wtf(TAG, "[$name] $message")
    }

    override fun never(message: String, throwable: Throwable?) {
        Log.wtf(TAG, "[$name] $message", throwable)
    }

    override fun child(childName: String): Logger {
        return LogcatLogger(childName(name, childName))
    }

    private companion object {
        private const val TAG = "App"
    }
}
