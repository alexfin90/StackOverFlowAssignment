package com.alexfin90.stackoverflowhomeassignment.core.domain

import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadQuestionsPageUseCaseTest {

    private val repository = FakeQuestionsRepository()
    private val useCase = LoadQuestionsPageUseCase(repository)

    private fun ids(result: QuestionsPageResult) =
        (result as QuestionsPageResult.Success).page.questions.map { it.id }

    @Test
    fun `first page returns the questions and hasMore`() = runTest {
        repository.onLatest = { _, _ -> QuestionsResult.Success(listOf(question(1), question(2)), hasMore = true) }

        val result = useCase(unansweredOnly = false, page = 1)

        assertEquals(listOf(1L, 2L), ids(result))
        assertTrue((result as QuestionsPageResult.Success).page.hasMore)
    }

    @Test
    fun `passes the filter and the page to the repository`() = runTest {
        useCase(unansweredOnly = true, page = 3)

        assertEquals(listOf(true to 3), repository.calls)
    }

    @Test
    fun `next page is added to the ones already loaded`() = runTest {
        repository.onLatest = { _, _ -> QuestionsResult.Success(listOf(question(3), question(4)), hasMore = false) }

        val result = useCase(unansweredOnly = false, page = 2, alreadyLoaded = listOf(question(1), question(2)))

        assertEquals(listOf(1L, 2L, 3L, 4L), ids(result))
        assertFalse((result as QuestionsPageResult.Success).page.hasMore)
    }

    @Test
    fun `a question that comes back in the next page appears once`() = runTest {
        repository.onLatest = { _, _ -> QuestionsResult.Success(listOf(question(2), question(3)), hasMore = false) }

        val result = useCase(unansweredOnly = false, page = 2, alreadyLoaded = listOf(question(1), question(2)))

        assertEquals(listOf(1L, 2L, 3L), ids(result))
    }

    @Test
    fun `page one starts over and ignores what was loaded`() = runTest {
        repository.onLatest = { _, _ -> QuestionsResult.Success(listOf(question(9)), hasMore = false) }

        val result = useCase(unansweredOnly = false, page = 1, alreadyLoaded = listOf(question(1)))

        assertEquals(listOf(9L), ids(result))
    }

    @Test
    fun `network error becomes a Network failure`() = runTest {
        repository.onLatest = { _, _ -> QuestionsResult.NetworkError("offline") }

        val result = useCase(unansweredOnly = false, page = 1) as QuestionsPageResult.Failure

        assertEquals(LoadFailure.Network, result.reason)
    }

    @Test
    fun `api error becomes a Server failure`() = runTest {
        repository.onLatest = { _, _ -> QuestionsResult.ApiError(500, "boom") }

        val result = useCase(unansweredOnly = false, page = 1) as QuestionsPageResult.Failure

        assertEquals(LoadFailure.Server, result.reason)
    }
}
