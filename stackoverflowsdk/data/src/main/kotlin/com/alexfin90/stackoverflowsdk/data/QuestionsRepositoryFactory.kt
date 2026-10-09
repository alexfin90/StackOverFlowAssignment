package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository

/**
 * Only way for the facade module to obtain the implementation: everything else in
 * this module is `internal`.
 *
 * Public only because Kotlin `internal` stops at the Gradle module boundary. Host apps cannot
 * reach it: the facade depends on `:data` with `implementation`. Not part of the supported API.
 *
 * @param loggingEnabled when `true` every call is logged to Logcat; `false` means no logging at all.
 */
public fun createQuestionsRepository(
    baseUrl: String,
    pageSize: Int,
    lastDays: Int,
    connectTimeoutSeconds: Long = 10,
    callTimeoutSeconds: Long = 30,
    loggingEnabled: Boolean = false,
    nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): QuestionsRepository = createQuestionsServices(
    baseUrl, pageSize, lastDays, connectTimeoutSeconds, callTimeoutSeconds, loggingEnabled, nowEpochSeconds,
).repository
