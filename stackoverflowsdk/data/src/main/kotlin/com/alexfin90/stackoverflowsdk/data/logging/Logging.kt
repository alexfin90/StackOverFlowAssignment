package com.alexfin90.stackoverflowsdk.data.logging

import android.util.Log
import com.alexfin90.stackoverflowsdk.api.StackOverflowLogger

/** Severity of an SDK log line. Mapped to `StackOverflowLogger.Level` when the host gives a logger. */
internal enum class LogLevel { DEBUG, WARN, ERROR }

/** Where log lines go. A function so tests can capture them without touching Logcat. */
internal typealias LogSink = (LogLevel, String) -> Unit

/** The default sink: Logcat, with a fixed tag. */
internal object LogcatSink : (LogLevel, String) -> Unit {

    private const val TAG = "StackOverflowSdk"

    override fun invoke(level: LogLevel, message: String) {
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, TAG, message)
    }
}

/** Sends the lines to the host's logger. */
internal class HostLoggerSink(private val logger: StackOverflowLogger) : (LogLevel, String) -> Unit {
    override fun invoke(level: LogLevel, message: String) {
        val publicLevel = when (level) {
            LogLevel.DEBUG -> StackOverflowLogger.Level.DEBUG
            LogLevel.WARN -> StackOverflowLogger.Level.WARN
            LogLevel.ERROR -> StackOverflowLogger.Level.ERROR
        }
        logger.log(publicLevel, message)
    }
}
