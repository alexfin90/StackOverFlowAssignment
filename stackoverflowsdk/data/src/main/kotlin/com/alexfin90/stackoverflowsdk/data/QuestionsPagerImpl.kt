package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.mapper.toDomain
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.domain.PagerError
import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The state of a paged list lives here, in a `MutableStateFlow` exposed read-only, like the repository
 * of a stream-based app would expose its list.
 *
 * Concurrency: a [Mutex] lets one request run at a time. `refresh` waits for its turn; `loadMore`
 * uses `tryLock` and simply returns if something is running, so calling it on every scroll event is
 * safe. `nextPage` and `snapshotEpochSeconds` are only touched while the lock is held.
 */
internal class QuestionsPagerImpl(
    private val remote: QuestionsRemoteDataSource,
    private val unansweredOnly: Boolean,
    private val pageSize: Int,
    private val lastDays: Int,
    private val nowEpochSeconds: () -> Long,
) : QuestionsPager {

    private val _state = MutableStateFlow(QuestionsPagerState())
    override val state: StateFlow<QuestionsPagerState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var nextPage = FIRST_PAGE

    /** "Now" when the first page was requested: the whole window is computed from it. */
    private var snapshotEpochSeconds = 0L

    override suspend fun refresh() {
        mutex.withLock {
            snapshotEpochSeconds = nowEpochSeconds()
            nextPage = FIRST_PAGE
            load(replace = true)
        }
    }

    override suspend fun loadMore() {
        if (!_state.value.hasMore) return
        if (!mutex.tryLock()) return // a refresh or another loadMore is running
        try {
            if (_state.value.hasMore) load(replace = false)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun load(replace: Boolean) {
        val previous = _state.value
        _state.value = QuestionsPagerState(
            questions = previous.questions,
            hasMore = previous.hasMore,
            status = if (replace) PagerStatus.Refreshing else PagerStatus.LoadingMore,
            isLoaded = previous.isLoaded,
        )
        val page = nextPage
        val result = try {
            remote.getLatestQuestions(
                // Same window for every page of this snapshot: it cannot slide while scrolling.
                fromDate = snapshotEpochSeconds - lastDays * SECONDS_PER_DAY,
                page = page,
                pageSize = pageSize,
                unansweredOnly = unansweredOnly,
                toDate = snapshotEpochSeconds,
            )
        } catch (e: CancellationException) {
            _state.value = previous // as if nothing had happened
            throw e
        }
        _state.value = when (result) {
            is RemoteResult.Success -> merge(previous, result, page, replace)
            is RemoteResult.Failure.Network -> failed(previous, PagerError.Network)
            is RemoteResult.Failure.Http,
            is RemoteResult.Failure.Malformed,
            is RemoteResult.Failure.Unexpected,
            -> failed(previous, PagerError.Api)
        }
    }

    private fun merge(
        previous: QuestionsPagerState,
        result: RemoteResult.Success<com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto>,
        page: Int,
        replace: Boolean,
    ): QuestionsPagerState =
        try {
            val loaded = result.value.items.map { it.toDomain() }
            // With the snapshot there should be no repeats; de-duplicating is a safety net.
            val all = if (replace) loaded else (previous.questions + loaded).distinctBy { it.id }
            nextPage = page + 1
            QuestionsPagerState(all, hasMore = result.value.hasMore, status = PagerStatus.Idle, isLoaded = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(previous, PagerError.Api) // a mapping bug is a value, never a crash
        }

    private fun failed(previous: QuestionsPagerState, error: PagerError) = QuestionsPagerState(
        questions = previous.questions, // what was already loaded stays on screen
        hasMore = previous.hasMore,
        status = PagerStatus.Failed(error),
        isLoaded = previous.isLoaded,
    )

    private companion object {
        const val FIRST_PAGE = 1
        const val SECONDS_PER_DAY = 86_400L
    }
}
