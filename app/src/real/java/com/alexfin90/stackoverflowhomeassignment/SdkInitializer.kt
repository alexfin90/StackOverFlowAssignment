package com.alexfin90.stackoverflowhomeassignment

import android.util.Log
import com.alexfin90.stackoverflowsdk.StackOverflowSdk
import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import com.alexfin90.stackoverflowsdk.api.StackOverflowLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * `real` flavor: configures the SDK once, at startup. `initialize` is cheap, so it runs on the main thread;
 * building the HTTP stack runs in the application scope, off the main thread.
 *
 * SDK logging goes to Timber, like the app's own logs, so the planted tree decides: the debug tree
 * shows every line, the release tree only warnings and errors. Timber is planted before this runs.
 */
fun initializeSdk(applicationScope: CoroutineScope) {
    StackOverflowSdk.initialize(StackOverflowConfig(logger = TimberSdkLogger))
    applicationScope.launch { StackOverflowSdk.warmUp() }
}

/** Sends the SDK's lines to Timber, with the SDK's tag. The lines never contain response content. */
private val TimberSdkLogger = StackOverflowLogger { level, message ->
    val priority = when (level) {
        StackOverflowLogger.Level.DEBUG -> Log.DEBUG
        StackOverflowLogger.Level.WARN -> Log.WARN
        StackOverflowLogger.Level.ERROR -> Log.ERROR
    }
    Timber.tag(SDK_LOG_TAG).log(priority, message)
}

private const val SDK_LOG_TAG = "StackOverflowSdk"
