package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.alexfin90.stackoverflowsdk.domain.InternalStackOverflowApi
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository

@OptIn(InternalStackOverflowApi::class)
class QuestionsRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: QuestionsRepository

    private val now = 1_700_000_000L

    @Before
    fun setUp() {
        server.start()
        repository = createQuestionsRepository(
            baseUrl = server.url("/").toString(),
            pageSize = 20,
            lastDays = 30,
            nowEpochSeconds = { now },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val twoQuestions = """
        {"items":[
          {"question_id":1,"title":"A &amp; B","link":"https://so/1","creation_date":1699999000,
           "answer_count":0,"score":1,"owner":{"display_name":"Ann"},"tags":["kotlin"],"unknown":"x"},
          {"question_id":2,"title":"Second","link":"https://so/2","creation_date":1699998000,
           "answer_count":2,"score":5,"tags":[]}
        ],"has_more":true}
    """.trimIndent()

    @Test
    fun `success maps questions and hasMore`() = runTest {
        server.enqueue(json(twoQuestions))

        val result = repository.getLatestQuestions()

        assertTrue(result is QuestionsResult.Success)
        result as QuestionsResult.Success
        assertEquals(2, result.questions.size)
        assertEquals("A & B", result.questions[0].title)
        assertTrue(result.hasMore)
    }

    @Test
    fun `latest request targets questions endpoint with 30 day window`() = runTest {
        server.enqueue(json(twoQuestions))

        repository.getLatestQuestions(page = 2)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/2.3/questions", url.encodedPath)
        assertEquals((now - 30 * 86_400L).toString(), url.queryParameter("fromdate"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("pagesize"))
        assertEquals("stackoverflow", url.queryParameter("site"))
        assertEquals("creation", url.queryParameter("sort"))
    }

    @Test
    fun `unanswered request targets no-answers endpoint`() = runTest {
        server.enqueue(json("""{"items":[],"has_more":false}"""))

        val result = repository.getLatestQuestions(unansweredOnly = true)

        assertEquals("/2.3/questions/no-answers", server.takeRequest().requestUrl!!.encodedPath)
        assertTrue((result as QuestionsResult.Success).questions.isEmpty())
        assertFalse(result.hasMore)
    }

    @Test
    fun `http error maps to ApiError with code`() = runTest {
        server.enqueue(json("""{"error_id":502,"error_message":"throttle_violation"}""", code = 400))

        val result = repository.getLatestQuestions()

        assertTrue(result is QuestionsResult.ApiError)
        assertEquals(400, (result as QuestionsResult.ApiError).httpCode)
    }

    @Test
    fun `malformed body maps to ApiError without code`() = runTest {
        server.enqueue(json("not json"))

        val result = repository.getLatestQuestions()

        assertTrue(result is QuestionsResult.ApiError)
        assertEquals(null, (result as QuestionsResult.ApiError).httpCode)
    }

    @Test
    fun `connection failure maps to NetworkError`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val result = repository.getLatestQuestions()

        assertTrue(result is QuestionsResult.NetworkError)
        assertNotNull(result)
    }
}
