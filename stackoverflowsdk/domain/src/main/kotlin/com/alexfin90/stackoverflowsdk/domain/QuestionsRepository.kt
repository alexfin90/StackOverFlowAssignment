package com.alexfin90.stackoverflowsdk.domain

/** Entry point for reading StackOverflow questions. Implementations never throw. */
public interface QuestionsRepository {

    /**
     * Questions created in the configured time window, newest first.
     *
     * @param unansweredOnly when `true` only questions with 0 answers are returned.
     * @param page 1-based page index.
     */
    public suspend fun getLatestQuestions(
        unansweredOnly: Boolean = false,
        page: Int = 1,
    ): QuestionsResult
}
