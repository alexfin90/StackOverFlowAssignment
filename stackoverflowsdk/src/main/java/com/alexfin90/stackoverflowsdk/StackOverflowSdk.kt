package com.alexfin90.stackoverflowsdk

import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import com.alexfin90.stackoverflowsdk.api.StackOverflowLogger
import com.alexfin90.stackoverflowsdk.data.createQuestionsRepository
import android.util.Log
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository

/**
 * Entry point of the SDK.
 *
 * There are two ways to get a [QuestionsRepository]:
 *
 * **1. Configure once, use everywhere** (typical for apps without dependency injection):
 *
 * ```
 * // Application.onCreate: cheap, no I/O, no extra thread
 * StackOverflowSdk.initialize(StackOverflowConfig(callTimeoutSeconds = 20))
 * // optional: build the HTTP stack off the main thread, so the first request is not slower
 * applicationScope.launch { StackOverflowSdk.warmUp() }
 *
 * // anywhere
 * when (val result = StackOverflowSdk.questions.getLatestQuestions(unansweredOnly = true)) {
 *     is QuestionsResult.Success -> ...
 *     is QuestionsResult.Failure -> when (result.error) {
 *         is SdkError.Network -> ...
 *         is SdkError.Api -> ...
 *     }
 * }
 * ```
 *
 * **2. Own the instance** with [create] (tests, several configurations, or your own DI container).
 *
 * Neither needs a `Context`. Network work happens only inside the `suspend` calls of the
 * repository, on the caller's coroutine.
 *
 * The shared instance is thread-safe and never throws: using [questions] without calling
 * [initialize] simply uses the default configuration.
 */
public object StackOverflowSdk {

    private val lock = Any()
    private var configuration: StackOverflowConfig? = null
    private var shared: QuestionsRepository? = null

    /** Receives the "initialize ignored" warning. Replaced in tests, which have no Logcat. */
    internal var warningSink: (String) -> Unit = { Log.w(LOG_TAG, it) }

    /**
     * Sets the configuration of the shared instance. Cheap: it only stores and validates the
     * configuration, so it is safe to call on the main thread at app startup.
     *
     * Only the **first** call takes effect, and only if the shared [questions] has not been used
     * yet. Later calls are ignored (a warning is logged if logging is enabled) and return `false`,
     * so the behaviour of a running SDK never changes underneath its callers.
     *
     * @return `true` if [config] was applied, `false` if it was ignored.
     */
    public fun initialize(config: StackOverflowConfig = StackOverflowConfig()): Boolean =
        synchronized(lock) {
            val current = configuration
            if (current != null || shared != null) {
                warn(
                    config = current ?: StackOverflowConfig(),
                    message = "initialize() ignored: the SDK is already initialized or in use",
                )
                false
            } else {
                configuration = config
                true
            }
        }

    /**
     * The shared [QuestionsRepository], built lazily and thread-safe on first access from the configuration
     * given to [initialize], or from the defaults if it was never called.
     *
     * The first access builds the HTTP stack (a few milliseconds): call [warmUp] from a
     * background thread at startup to avoid paying that on the main thread.
     */
    public val questions: QuestionsRepository
        get() = synchronized(lock) {
            shared ?: build(configuration ?: StackOverflowConfig().also { configuration = it })
                .also { shared = it }
        }

    /**
     * Builds the shared repository now. Idempotent. It does no network I/O but allocates the HTTP
     * client, so call it **off the main thread** (the SDK deliberately creates no threads of its
     * own: the host decides where this runs).
     */
    public fun warmUp() {
        questions
    }

    /**
     * Creates an independent repository, ignoring [initialize]. Keep and reuse the instance: it
     * owns the HTTP client.
     */
    public fun create(config: StackOverflowConfig = StackOverflowConfig()): QuestionsRepository =
        build(config)

    /** Clears the shared state so tests can start from scratch. Not part of the public API. */
    internal fun resetForTesting() {
        synchronized(lock) {
            configuration = null
            shared = null
            warningSink = { Log.w(LOG_TAG, it) }
        }
    }

    private fun build(config: StackOverflowConfig): QuestionsRepository =
        createQuestionsRepository(
            baseUrl = config.baseUrl,
            pageSize = config.pageSize,
            lastDays = config.lastDays,
            connectTimeoutSeconds = config.connectTimeoutSeconds,
            callTimeoutSeconds = config.callTimeoutSeconds,
            loggingEnabled = config.loggingEnabled,
            logger = config.logger,
        )

    /** Goes to the host's logger if there is one, to Logcat if logging is on, nowhere otherwise. */
    private fun warn(config: StackOverflowConfig, message: String) {
        try {
            val logger = config.logger
            when {
                logger != null -> logger.log(StackOverflowLogger.Level.WARN, message)
                config.loggingEnabled -> warningSink(message)
            }
        } catch (_: Exception) {
            // Logging must never break the SDK.
        }
    }

    private const val LOG_TAG = "StackOverflowSdk"
}
