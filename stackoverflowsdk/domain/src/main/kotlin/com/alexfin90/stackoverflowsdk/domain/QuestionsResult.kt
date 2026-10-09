package com.alexfin90.stackoverflowsdk.domain

/**
 * Outcome of a questions request.
 *
 * The SDK never throws across its public boundary: every failure is reported as a [Failure], whose
 * [SdkError] says why, so the host app can handle it explicitly.
 */
public sealed interface QuestionsResult {

    public class Success(
        public val questions: List<Question>,
        /** `true` when more pages are available after this one. */
        public val hasMore: Boolean,
    ) : QuestionsResult

    /** The request failed: [error] says why. */
    public class Failure(public val error: SdkError) : QuestionsResult
}
