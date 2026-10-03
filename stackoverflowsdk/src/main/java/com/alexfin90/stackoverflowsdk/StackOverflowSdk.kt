package com.alexfin90.stackoverflowsdk

import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import com.alexfin90.stackoverflowsdk.data.createQuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.LogLevel
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger

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
 * when (val result = StackOverflowSdk.questionsRepository.getLatestQuestions(unansweredOnly = true)) {
 *     is QuestionsResult.Success -> ...
 *     is QuestionsResult.NetworkError -> ...
 *     is QuestionsResult.ApiError -> ...
 * }
 * ```
 *
 * **2. Own the instance** with [create] (tests, several configurations, or your own DI container).
 *
 * Neither needs a `Context`. Network work happens only inside the `suspend` calls of the
 * repository, on the caller's coroutine.
 *
 * The shared instance is thread-safe and never throws: using [questionsRepository] without calling
 * [initialize] simply uses the default configuration.
 */
public object StackOverflowSdk {

    private val lock = Any()
    private var configuration: StackOverflowConfig? = null
    private var shared: QuestionsRepository? = null

    /**
     * Sets the configuration of the shared instance. Cheap: it only stores and validates the
     * configuration, so it is safe to call on the main thread at app startup.
     *
     * Only the **first** call takes effect, and only if the shared [questionsRepository] has not been used
     * yet. Later calls are ignored (a warning is logged if logging is enabled) and return `false`,
     * so the behaviour of a running SDK never changes underneath its callers.
     *
     * @return `true` if [config] was applied, `false` if it was ignored.
     */
    public fun initialize(config: StackOverflowConfig = StackOverflowConfig()): Boolean =
        synchronized(lock) {
            val current = configuration
            if (current != null || shared != null) {
                resolveLogger(current ?: StackOverflowConfig())?.logSafely(
                    LogLevel.WARN,
                    "initialize() ignored: the SDK is already initialized or in use",
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
    public val questionsRepository: QuestionsRepository
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
        questionsRepository
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
        }
    }

    private fun build(config: StackOverflowConfig): QuestionsRepository =
        createQuestionsRepository(
            baseUrl = config.baseUrl,
            pageSize = config.pageSize,
            lastDays = config.lastDays,
            connectTimeoutSeconds = config.connectTimeoutSeconds,
            callTimeoutSeconds = config.callTimeoutSeconds,
            logger = resolveLogger(config),
        )

    private fun resolveLogger(config: StackOverflowConfig): StackOverflowLogger? =
        if (config.loggingEnabled) config.logger ?: LogcatLogger else null

    private fun StackOverflowLogger.logSafely(level: LogLevel, message: String) {
        try {
            log(level, message)
        } catch (_: Exception) {
            // A faulty host logger must not break the SDK.
        }
    }
}
