package com.alexfin90.stackoverflowsdk.data.logging

import android.util.Log

/** Severity of an SDK log line. Internal: the host only has an on/off switch. */
internal enum class LogLevel { DEBUG, WARN, ERROR }

/** Where log lines go. A function so tests can capture them without touching Logcat. */
internal typealias LogSink = (LogLevel, String) -> Unit

/** The only real sink: Logcat, with a fixed tag. */
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
