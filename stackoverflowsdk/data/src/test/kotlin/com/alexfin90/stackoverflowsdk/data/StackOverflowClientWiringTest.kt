package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.domain.StackOverflowClient
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End to end through the public factory (real Retrofit + MockWebServer) to guard the wiring.
 * The detailed behaviour is covered by the data source and client tests.
 */
@RunWith(RobolectricTestRunner::class)
class StackOverflowClientWiringTest {

    private val server = MockWebServer()
    private lateinit var client: StackOverflowClient

    @Before
    fun setUp() {
        server.start()
        client = createStackOverflowClient(
            baseUrl = server.url("/").toString(),
            pageSize = 20,
            lastDays = 30,
            nowEpochSeconds = { 1_700_000_000L },
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
    fun `success is mapped to domain questions`() = runTest {
        server.enqueue(json(twoQuestions))

        val result = client.getLatestQuestions()

        assertTrue(result is QuestionsResult.Success)
        result as QuestionsResult.Success
        assertEquals(2, result.questions.size)
        assertEquals("A & B", result.questions[0].title)
        assertTrue(result.hasMore)
    }

    @Test
    fun `unanswered filter hits the no-answers endpoint`() = runTest {
        server.enqueue(json("""{"items":[],"has_more":false}"""))

        val result = client.getLatestQuestions(unansweredOnly = true)

        assertEquals("/2.3/questions/no-answers", server.takeRequest().requestUrl!!.encodedPath)
        assertTrue((result as QuestionsResult.Success).questions.isEmpty())
        assertFalse(result.hasMore)
    }

    @Test
    fun `connection failure maps to NetworkError`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertTrue(client.getLatestQuestions() is QuestionsResult.NetworkError)
    }
}
