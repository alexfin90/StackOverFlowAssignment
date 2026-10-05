package com.alexfin90.stackoverflowsdk.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * A paged, growing list of the latest questions, with its state, so the host does not have to join
 * pages, remove duplicates or decide what happens when a request fails.
 *
 * ```
 * val pager = StackOverflowSdk.pagers.create(unansweredOnly = true)
 * scope.launch { pager.state.collect { render(it) } }
 * scope.launch { pager.refresh() }       // first page
 * scope.launch { pager.loadMore() }      // when the user nears the end of the list
 * ```
 *
 * **One pager per filter**: [create][QuestionsPagerFactory.create] a new one when `unansweredOnly`
 * changes. A pager keeps its questions in memory for as long as you keep it.
 *
 * Rules:
 * - The pager starts **empty and idle**; call [refresh] first.
 * - [refresh] starts over from page 1 and replaces the list. It waits for a request already running.
 * - [loadMore] adds the next page. It does nothing if there is no next page or if a request is
 *   already running, so it is safe to call it on every scroll event.
 * - The pager creates **no threads and no scope**: [refresh] and [loadMore] run in the coroutine of
 *   the caller. Cancelling that coroutine cancels the request and leaves the state as it was.
 * - **Nothing is thrown**: a failure becomes [PagerStatus.Failed] and the questions already loaded
 *   are kept.
 * - The window of "the last N days" is **fixed when [refresh] runs** (a snapshot), so a question
 *   posted while the user scrolls does not slide the pages and cannot show up twice. New questions
 *   appear at the next [refresh].
 */
public interface QuestionsPager {

    /** Everything the UI needs: the questions so far, whether there are more, and what is happening. */
    public val state: StateFlow<QuestionsPagerState>

    /** Starts over from the first page. */
    public suspend fun refresh()

    /** Adds the next page, if there is one and nothing else is loading. */
    public suspend fun loadMore()
}

public class QuestionsPagerState(
    /** The questions loaded so far, in order, without duplicates. */
    public val questions: List<Question> = emptyList(),
    /** `true` when another page can be loaded with [QuestionsPager.loadMore]. */
    public val hasMore: Boolean = false,
    public val status: PagerStatus = PagerStatus.Idle,
    /**
     * `true` once a page has been loaded successfully. It tells "nothing loaded yet" (empty, about to
     * load) apart from "loaded and there is nothing to show" (empty, [isLoaded]).
     */
    public val isLoaded: Boolean = false,
)

public sealed interface PagerStatus {
    /** Nothing is happening. */
    public data object Idle : PagerStatus

    /** [QuestionsPager.refresh] is running; the previous questions, if any, are still in the state. */
    public data object Refreshing : PagerStatus

    /** [QuestionsPager.loadMore] is running. */
    public data object LoadingMore : PagerStatus

    /** The last request failed. The questions already loaded are kept; calling again retries. */
    public class Failed(public val error: PagerError) : PagerStatus
}

public enum class PagerError {
    /** No usable connection. Usually retryable. */
    Network,

    /** The server answered badly or the answer could not be read. */
    Api,
}

/** Creates pagers that share the SDK's HTTP client and configuration. */
public interface QuestionsPagerFactory {
    public fun create(unansweredOnly: Boolean = false): QuestionsPager
}
