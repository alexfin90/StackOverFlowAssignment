package com.alexfin90.stackoverflowhomeassignment

import com.alexfin90.stackoverflowsdk.StackOverflowSdk
import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Configures the SDK once, at startup. `initialize` is cheap, so it runs on the main thread;
 * building the HTTP stack runs in the application scope, off the main thread.
 *
 * SDK logging goes to Logcat (tag `StackOverflowSdk`) in debug builds only.
 */
fun initializeSdk(applicationScope: CoroutineScope) {
    StackOverflowSdk.initialize(StackOverflowConfig(loggingEnabled = BuildConfig.DEBUG))
    applicationScope.launch { StackOverflowSdk.warmUp() }
}
