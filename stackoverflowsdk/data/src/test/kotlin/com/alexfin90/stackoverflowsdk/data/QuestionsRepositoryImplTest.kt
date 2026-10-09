package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.remote.OwnerDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import com.alexfin90.stackoverflowsdk.domain.SdkError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The repository against a fake data source: no HTTP stack involved. */
@RunWith(RobolectricTestRunner::class)
class QuestionsRepositoryImplTest {

    private class FakeRemote(
        var result: RemoteResult<QuestionsResponseDto>,
        var questionResult: RemoteResult<QuestionsResponseDto> = result,
    ) : QuestionsRemoteDataSource {
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

        val requestedIds = mutableListOf<Long>()

        override suspend fun getQuestion(id: Long): RemoteResult<QuestionsResponseDto> {
            requestedIds += id
            return questionResult
        }
    }

    private val now = 1_700_000_000L

    private fun repository(remote: QuestionsRemoteDataSource) =
        QuestionsRepositoryImpl(remote, pageSize = 20, lastDays = 30, nowEpochSeconds = { now })

    private fun dto(id: Long, title: String = "Q$id") =
        QuestionDto(id, title, "https://so/$id", 1_699_999_000, owner = OwnerDto("Ann"))

    @Test
    fun `success maps dtos to domain questions and keeps hasMore`() = runTest {
        val remote = FakeRemote(
            RemoteResult.Success(QuestionsResponseDto(listOf(dto(1, "A &amp; B"), dto(2)), hasMore = true)),
        )

        val result = repository(remote).getLatestQuestions() as QuestionsResult.Success

        assertEquals(listOf(1L, 2L), result.questions.map { it.id })
        assertEquals("A & B", result.questions[0].title)
        assertTrue(result.hasMore)
    }

    @Test
    fun `asks the remote for the last 30 days with the caller parameters`() = runTest {
        val remote = FakeRemote(RemoteResult.Success(QuestionsResponseDto()))

        repository(remote).getLatestQuestions(unansweredOnly = true, page = 3)

        assertEquals(
            FakeRemote.Call(fromDate = now - 30 * 86_400L, page = 3, pageSize = 20, unansweredOnly = true),
            remote.calls.single(),
        )
    }

    @Test
    fun `network failure becomes NetworkError`() = runTest {
        val remote = FakeRemote(RemoteResult.Failure.Network("offline"))

        val result = repository(remote).getLatestQuestions().error() as SdkError.Network

        assertEquals("offline", result.message)
    }

    @Test
    fun `http failure becomes ApiError with the code`() = runTest {
        val remote = FakeRemote(RemoteResult.Failure.Http(400, "Bad Request"))

        val result = repository(remote).getLatestQuestions().error() as SdkError.Api

        assertEquals(400, result.httpCode)
    }

    @Test
    fun `malformed body becomes ApiError without a code`() = runTest {
        val result = repository(FakeRemote(RemoteResult.Failure.Malformed("bad json")))
            .getLatestQuestions().error() as SdkError.Api

        assertNull(result.httpCode)
    }

    @Test
    fun `unexpected failure becomes ApiError without a code`() = runTest {
        val result = repository(FakeRemote(RemoteResult.Failure.Unexpected("bug")))
            .getLatestQuestions().error() as SdkError.Api

        assertNull(result.httpCode)
        assertEquals("bug", result.message)
    }

    @Test
    fun `getQuestion maps the first item`() = runTest {
        val remote = FakeRemote(
            RemoteResult.Success(QuestionsResponseDto()),
            questionResult = RemoteResult.Success(QuestionsResponseDto(listOf(dto(7, "A &amp; B")))),
        )

        val result = repository(remote).getQuestion(7) as QuestionResult.Success

        assertEquals(listOf(7L), remote.requestedIds)
        assertEquals("A & B", result.question.title)
    }

    @Test
    fun `getQuestion with no items is NotFound`() = runTest {
        val remote = FakeRemote(
            RemoteResult.Success(QuestionsResponseDto()),
            questionResult = RemoteResult.Success(QuestionsResponseDto()),
        )

        assertEquals(QuestionResult.NotFound, repository(remote).getQuestion(999))
    }

    @Test
    fun `getQuestion failures map to the right results`() = runTest {
        fun withFailure(f: RemoteResult.Failure) =
            FakeRemote(RemoteResult.Success(QuestionsResponseDto()), questionResult = f)

        assertTrue(repository(withFailure(RemoteResult.Failure.Network("x"))).getQuestion(1).error() is SdkError.Network)
        assertEquals(500, (repository(withFailure(RemoteResult.Failure.Http(500, "e"))).getQuestion(1).error() as SdkError.Api).httpCode)
        assertNull((repository(withFailure(RemoteResult.Failure.Malformed("m"))).getQuestion(1).error() as SdkError.Api).httpCode)
        assertNull((repository(withFailure(RemoteResult.Failure.Unexpected("u"))).getQuestion(1).error() as SdkError.Api).httpCode)
    }

    private fun QuestionsResult.error(): SdkError = (this as QuestionsResult.Failure).error

    private fun QuestionResult.error(): SdkError = (this as QuestionResult.Failure).error
}
