package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.domain.LogLevel
import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.StackOverflowClient
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingStackOverflowClientTest {

    private val lines = mutableListOf<Pair<LogLevel, String>>()
    private val logger = StackOverflowLogger { level, message -> lines += level to message }

    private class FakeDelegate(var result: () -> QuestionsResult) : StackOverflowClient {
        override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int) = result()
    }

    private fun client(delegate: StackOverflowClient, log: StackOverflowLogger = logger) =
        LoggingStackOverflowClient(delegate, log, nanoTime = fakeClock())

    // Each call to nanoTime advances 5 ms, so the logged duration is deterministic.
    private fun fakeClock(): () -> Long {
        var now = 0L
        return { now.also { now += 5_000_000L } }
    }

    private val secret = Question(1, "Secret title", "https://so/1", 0, 0, 0, "Secret owner", emptyList())

    @Test
    fun `success is logged at debug with counts and duration, never with content`() = runTest {
        val repo = client(FakeDelegate { QuestionsResult.Success(listOf(secret), hasMore = true) })

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
        val repo = client(FakeDelegate { QuestionsResult.NetworkError("Unable to resolve host x") })

        repo.getLatestQuestions()

        val (level, message) = lines.single()
        assertEquals(LogLevel.WARN, level)
        assertTrue(message.contains("NetworkError"))
        assertFalse(message.contains("resolve host"))
    }

    @Test
    fun `api error is logged as an error with the http code`() = runTest {
        val repo = client(FakeDelegate { QuestionsResult.ApiError(400, "throttle_violation") })

        repo.getLatestQuestions()

        val (level, message) = lines.single()
        assertEquals(LogLevel.ERROR, level)
        assertTrue(message.contains("ApiError(httpCode=400)"))
        assertFalse(message.contains("throttle"))
    }

    @Test
    fun `the result is returned untouched`() = runTest {
        val expected = QuestionsResult.NetworkError("x")

        assertSame(expected, client(FakeDelegate { expected }).getLatestQuestions())
    }

    @Test
    fun `a throwing logger does not break the request`() = runTest {
        val repo = client(
            FakeDelegate { QuestionsResult.Success(emptyList(), false) },
            log = { _, _ -> error("logger bug") },
        )

        assertTrue(repo.getLatestQuestions() is QuestionsResult.Success)
    }

    @Test
    fun `cancellation propagates and nothing is logged`() = runTest {
        val repo = client(FakeDelegate { throw CancellationException("cancelled") })

        try {
            repo.getLatestQuestions()
            error("expected a CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertTrue(lines.isEmpty())
    }
}
