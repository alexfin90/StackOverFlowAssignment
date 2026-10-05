package com.alexfin90.stackoverflowhomeassignment.feature.home

import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerFactory
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A pager the test drives by hand: it records the calls and its state is set by the test. */
class FakePager(val unansweredOnly: Boolean) : QuestionsPager {
    override val state: MutableStateFlow<QuestionsPagerState> = MutableStateFlow(QuestionsPagerState())
    var refreshes = 0
    var loadMores = 0

    override suspend fun refresh() { refreshes++ }
    override suspend fun loadMore() { loadMores++ }
}

class FakePagerFactory : QuestionsPagerFactory {
    val created = mutableListOf<FakePager>()
    val last: FakePager get() = created.last()

    override fun create(unansweredOnly: Boolean): QuestionsPager = FakePager(unansweredOnly).also { created += it }
}

fun question(id: Long) = Question(
    id = id,
    title = "Question $id",
    link = "https://stackoverflow.com/q/$id",
    creationDateEpochSeconds = 1_700_000_000,
    answerCount = 1,
    score = 1,
    ownerName = "user$id",
    tags = emptyList(),
)
