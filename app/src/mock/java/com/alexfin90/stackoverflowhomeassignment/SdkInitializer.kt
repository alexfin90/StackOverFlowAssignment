package com.alexfin90.stackoverflowhomeassignment

import kotlinx.coroutines.CoroutineScope

/**
 * `mock` flavor: nothing to initialize. The app runs on in-memory data and never touches the
 * SDK, so its HTTP stack is not even built. The `real` flavor configures and warms up the SDK here.
 */
@Suppress("UNUSED_PARAMETER")
fun initializeSdk(applicationScope: CoroutineScope) = Unit
