package com.alexfin90.stackoverflowhomeassignment.fake

import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerFactory
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [QuestionsPager] with a small delay, so loading states are visible. */
class FakeQuestionsPager(
    unansweredOnly: Boolean,
    private val latencyMillis: Long = 800,
    private val pageSize: Int = 10,
) : QuestionsPager {

    private val source = if (unansweredOnly) fakeQuestions.filter { it.answerCount == 0 } else fakeQuestions
    private val _state = MutableStateFlow(QuestionsPagerState())
    override val state: StateFlow<QuestionsPagerState> = _state.asStateFlow()
    private var loading = false // the fake is used from one thread; the real pager uses a Mutex

    override suspend fun refresh() = load(replace = true)

    override suspend fun loadMore() {
        if (!_state.value.hasMore || loading) return
        load(replace = false)
    }

    private suspend fun load(replace: Boolean) {
        loading = true
        val previous = _state.value
        _state.value = QuestionsPagerState(
            questions = previous.questions,
            hasMore = previous.hasMore,
            status = if (replace) PagerStatus.Refreshing else PagerStatus.LoadingMore,
            isLoaded = previous.isLoaded,
        )
        try {
            delay(latencyMillis)
        } catch (e: CancellationException) {
            _state.value = previous
            throw e
        } finally {
            loading = false
        }
        val from = if (replace) 0 else previous.questions.size
        val page = source.drop(from).take(pageSize)
        _state.value = QuestionsPagerState(
            questions = (if (replace) emptyList() else previous.questions) + page,
            hasMore = from + pageSize < source.size,
            status = PagerStatus.Idle,
            isLoaded = true,
        )
    }
}

class FakeQuestionsPagerFactory : QuestionsPagerFactory {
    override fun create(unansweredOnly: Boolean): QuestionsPager = FakeQuestionsPager(unansweredOnly)
}
