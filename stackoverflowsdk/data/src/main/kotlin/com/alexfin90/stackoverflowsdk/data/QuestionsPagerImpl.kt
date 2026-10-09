package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.mapper.toDomain
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.data.remote.toSdkError
import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import com.alexfin90.stackoverflowsdk.domain.SdkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The state of a paged list lives here, in a `MutableStateFlow` exposed read-only, like the repository
 * of a stream-based app would expose its list.
 *
 * Two tools, each for what it can do:
 * - `StateFlow.update` makes every state transition atomic (a pure read-modify-write);
 * - the [Mutex] makes the **whole request** one unit: a `suspend` network call cannot be wrapped in
 *   `update` (its lambda must be pure and may be re-run), so without the lock two callers could both
 *   pass the "can I load?" check and ask for the same page, and `nextPage` would skip one.
 *
 * `refresh` waits for its turn; `loadMore` uses `tryLock` and simply returns if something is running,
 * so calling it on every scroll event is safe. `nextPage` and `snapshotEpochSeconds` are plain
 * variables, only touched while the lock is held.
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
        val statusBefore = _state.value.status // to restore it if the request is cancelled
        _state.update { it.withStatus(if (replace) PagerStatus.Refreshing else PagerStatus.LoadingMore) }
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
            _state.update { it.withStatus(statusBefore) } // as if nothing had happened
            throw e
        }
        when (result) {
            is RemoteResult.Success -> onSuccess(result, page, replace)
            is RemoteResult.Failure -> onFailure(result.toSdkError())
        }
    }

    private fun onSuccess(
        result: RemoteResult.Success<com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto>,
        page: Int,
        replace: Boolean,
    ) {
        val loaded = try {
            result.value.items.map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(SdkError.Api(null, e.message ?: e.javaClass.simpleName)) // a mapping bug is a value, never a crash
            return
        }
        nextPage = page + 1
        _state.update { current ->
            // With the snapshot there should be no repeats; de-duplicating is a safety net.
            val all = if (replace) loaded else (current.questions + loaded).distinctBy { it.id }
            QuestionsPagerState(all, hasMore = result.value.hasMore, status = PagerStatus.Idle, isLoaded = true)
        }
    }

    /** What was already loaded stays on screen; only the status changes. */
    private fun onFailure(error: SdkError) {
        _state.update { it.withStatus(PagerStatus.Failed(error)) }
    }

    private fun QuestionsPagerState.withStatus(status: PagerStatus) =
        QuestionsPagerState(questions, hasMore, status, isLoaded)

    private companion object {
        const val FIRST_PAGE = 1
        const val SECONDS_PER_DAY = 86_400L
    }
}
