package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.remote.OwnerDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The client against a fake data source: no HTTP stack involved. */
@RunWith(RobolectricTestRunner::class)
class StackOverflowClientImplTest {

    private class FakeRemote(var result: RemoteResult<QuestionsResponseDto>) : QuestionsRemoteDataSource {
        data class Call(val fromDate: Long, val page: Int, val pageSize: Int, val unansweredOnly: Boolean)

        val calls = mutableListOf<Call>()

        override suspend fun getLatestQuestions(
            fromDate: Long,
            page: Int,
            pageSize: Int,
            unansweredOnly: Boolean,
        ): RemoteResult<QuestionsResponseDto> {
            calls += Call(fromDate, page, pageSize, unansweredOnly)
            return result
        }
    }

    private val now = 1_700_000_000L

    private fun client(remote: QuestionsRemoteDataSource) =
        StackOverflowClientImpl(remote, pageSize = 20, lastDays = 30, nowEpochSeconds = { now })

    private fun dto(id: Long, title: String = "Q$id") =
        QuestionDto(id, title, "https://so/$id", 1_699_999_000, owner = OwnerDto("Ann"))

    @Test
    fun `success maps dtos to domain questions and keeps hasMore`() = runTest {
        val remote = FakeRemote(
            RemoteResult.Success(QuestionsResponseDto(listOf(dto(1, "A &amp; B"), dto(2)), hasMore = true)),
        )

        val result = client(remote).getLatestQuestions() as QuestionsResult.Success

        assertEquals(listOf(1L, 2L), result.questions.map { it.id })
        assertEquals("A & B", result.questions[0].title)
        assertTrue(result.hasMore)
    }

    @Test
    fun `asks the remote for the last 30 days with the caller parameters`() = runTest {
        val remote = FakeRemote(RemoteResult.Success(QuestionsResponseDto()))

        client(remote).getLatestQuestions(unansweredOnly = true, page = 3)

        assertEquals(
            FakeRemote.Call(fromDate = now - 30 * 86_400L, page = 3, pageSize = 20, unansweredOnly = true),
            remote.calls.single(),
        )
    }

    @Test
    fun `network failure becomes NetworkError`() = runTest {
        val remote = FakeRemote(RemoteResult.Failure.Network("offline"))

        val result = client(remote).getLatestQuestions() as QuestionsResult.NetworkError

        assertEquals("offline", result.message)
    }

    @Test
    fun `http failure becomes ApiError with the code`() = runTest {
        val remote = FakeRemote(RemoteResult.Failure.Http(400, "Bad Request"))

        val result = client(remote).getLatestQuestions() as QuestionsResult.ApiError

        assertEquals(400, result.httpCode)
    }

    @Test
    fun `malformed body becomes ApiError without a code`() = runTest {
        val result = client(FakeRemote(RemoteResult.Failure.Malformed("bad json")))
            .getLatestQuestions() as QuestionsResult.ApiError

        assertNull(result.httpCode)
    }

    @Test
    fun `unexpected failure becomes ApiError without a code`() = runTest {
        val result = client(FakeRemote(RemoteResult.Failure.Unexpected("bug")))
            .getLatestQuestions() as QuestionsResult.ApiError

        assertNull(result.httpCode)
        assertEquals("bug", result.message)
    }
}
