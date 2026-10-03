package com.alexfin90.stackoverflowsdk

import android.util.Log
import com.alexfin90.stackoverflowsdk.domain.LogLevel
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger

/** Default sink when logging is enabled and the host did not provide its own logger. */
internal object LogcatLogger : StackOverflowLogger {

    private const val TAG = "StackOverflowSdk"

    override fun log(level: LogLevel, message: String) {
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, TAG, message)
    }
}
