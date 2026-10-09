package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.logging.LogLevel
import com.alexfin90.stackoverflowsdk.data.logging.LogSink
import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import com.alexfin90.stackoverflowsdk.domain.SdkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingQuestionsRepositoryTest {

    private val lines = mutableListOf<Pair<LogLevel, String>>()
    private val sink: LogSink = { level, message -> lines += level to message }

    // `result` is last on purpose: a trailing lambda binds to the last parameter.
    private class FakeDelegate(
        var questionResult: () -> QuestionResult = { QuestionResult.NotFound },
        var result: () -> QuestionsResult = { QuestionsResult.Success(emptyList(), false) },
    ) : QuestionsRepository {
        override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int) = result()
        override suspend fun getQuestion(id: Long) = questionResult()
    }

    private fun repository(delegate: QuestionsRepository, log: LogSink = sink) =
        LoggingQuestionsRepository(delegate, log, nanoTime = fakeClock())

    // Each call to nanoTime advances 5 ms, so the logged duration is deterministic.
    private fun fakeClock(): () -> Long {
        var now = 0L
        return { now.also { now += 5_000_000L } }
    }

    private val secret = Question(1, "Secret title", "https://so/1", 0, 0, 0, "Secret owner", emptyList())

    @Test
    fun `success is logged at debug with counts and duration, never with content`() = runTest {
        val repo = repository(FakeDelegate { QuestionsResult.Success(listOf(secret), hasMore = true) })

        val result = repo.getLatestQuestions(unansweredOnly = true, page = 2)

        assertTrue(result is QuestionsResult.Success)
        val (level, message) = lines.single()
        assertEquals(LogLevel.DEBUG, level)
        assertEquals(
            "getLatestQuestions(unansweredOnly=true, page=2) -> Success(items=1, hasMore=true) in 5 ms",
            message,
        )
        assertFalse(message.contains("Secret"))
    }

    @Test
    fun `network error is logged as a warning without the exception message`() = runTest {
        val repo = repository(FakeDelegate { QuestionsResult.Failure(SdkError.Network("Unable to resolve host x")) })

        repo.getLatestQuestions()

        val (level, message) = lines.single()
        assertEquals(LogLevel.WARN, level)
        assertTrue(message.contains("NetworkError"))
        assertFalse(message.contains("resolve host"))
    }

    @Test
    fun `api error is logged as an error with the http code`() = runTest {
        val repo = repository(FakeDelegate { QuestionsResult.Failure(SdkError.Api(400, "throttle_violation")) })

        repo.getLatestQuestions()

        val (level, message) = lines.single()
        assertEquals(LogLevel.ERROR, level)
        assertTrue(message.contains("ApiError(httpCode=400)"))
        assertFalse(message.contains("throttle"))
    }

    @Test
    fun `the result is returned untouched`() = runTest {
        val expected = QuestionsResult.Failure(SdkError.Network("x"))

        assertSame(expected, repository(FakeDelegate { expected }).getLatestQuestions())
    }

    @Test
    fun `a failing sink does not break the request`() = runTest {
        val repo = repository(
            FakeDelegate { QuestionsResult.Success(emptyList(), false) },
            log = { _, _ -> error("logger bug") },
        )

        assertTrue(repo.getLatestQuestions() is QuestionsResult.Success)
    }

    @Test
    fun `cancellation propagates and nothing is logged`() = runTest {
        val repo = repository(FakeDelegate { throw CancellationException("cancelled") })

        try {
            repo.getLatestQuestions()
            error("expected a CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `getQuestion success is logged at debug without the content`() = runTest {
        val repo = repository(FakeDelegate(questionResult = { QuestionResult.Success(secret) }))

        repo.getQuestion(42)

        val (level, message) = lines.single()
        assertEquals(LogLevel.DEBUG, level)
        assertEquals("getQuestion(id=42) -> Success in 5 ms", message)
        assertFalse(message.contains("Secret"))
    }

    @Test
    fun `getQuestion not found is a debug line and errors keep their level`() = runTest {
        repository(FakeDelegate(questionResult = { QuestionResult.NotFound })).getQuestion(1)
        repository(FakeDelegate(questionResult = { QuestionResult.Failure(SdkError.Network("offline")) })).getQuestion(1)
        repository(FakeDelegate(questionResult = { QuestionResult.Failure(SdkError.Api(500, "e")) })).getQuestion(1)

        assertEquals(listOf(LogLevel.DEBUG, LogLevel.WARN, LogLevel.ERROR), lines.map { it.first })
        assertTrue(lines.last().second.contains("ApiError(httpCode=500)"))
    }
}
