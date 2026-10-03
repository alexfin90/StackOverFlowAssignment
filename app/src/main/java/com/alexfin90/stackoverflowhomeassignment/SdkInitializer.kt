package com.alexfin90.stackoverflowhomeassignment

import com.alexfin90.stackoverflowsdk.StackOverflowSdk
import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import com.alexfin90.stackoverflowsdk.domain.LogLevel
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Configures the SDK once, at startup. `initialize` is cheap, so it runs on the main thread;
 * building the HTTP stack runs in the application scope, off the main thread.
 */
fun initializeSdk(applicationScope: CoroutineScope) {
    StackOverflowSdk.initialize(
        config = StackOverflowConfig(
            loggingEnabled = BuildConfig.DEBUG,
            logger = TimberSdkLogger,
        ),
    )
    applicationScope.launch { StackOverflowSdk.warmUp() }
}

/** Forwards the SDK's log lines to Timber, so they follow the app's logging setup. */
private val TimberSdkLogger = StackOverflowLogger { level, message ->
    val priority = when (level) {
        LogLevel.DEBUG -> android.util.Log.DEBUG
        LogLevel.WARN -> android.util.Log.WARN
        LogLevel.ERROR -> android.util.Log.ERROR
    }
    Timber.tag("StackOverflowSdk").log(priority, message)
}
