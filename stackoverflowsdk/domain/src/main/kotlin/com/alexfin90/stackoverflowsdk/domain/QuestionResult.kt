package com.alexfin90.stackoverflowsdk.domain

/** Outcome of loading a single question. Like [QuestionsResult], it is never thrown. */
public sealed interface QuestionResult {

    public class Success(public val question: Question) : QuestionResult

    /** The id does not exist (or the question was deleted). */
    public data object NotFound : QuestionResult

    /** The request failed: [error] says why. */
    public class Failure(public val error: SdkError) : QuestionResult
}
