package com.alexfin90.stackoverflowsdk.data.remote

import com.alexfin90.stackoverflowsdk.data.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/** Transport behaviour: requests sent and exceptions translated, against a real HTTP server. */
class UrlConnectionQuestionsRemoteDataSourceTest {

    private val server = MockWebServer()
    private lateinit var remote: QuestionsRemoteDataSource

    private fun remoteFor(baseUrl: String, callTimeoutMillis: Long = 10_000): QuestionsRemoteDataSource =
        UrlConnectionQuestionsRemoteDataSource(
            baseUrl = baseUrl,
            json = NetworkModule.provideJson(),
            connectTimeoutMillis = 5_000,
            callTimeoutMillis = callTimeoutMillis,
        )

    @Before
    fun setUp() {
        server.start()
        remote = remoteFor(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    // HttpURLConnection retries a failed GET once by itself, so "down" must fail both attempts.
    private fun serverDown() = repeat(2) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
    }

    private suspend fun latest(unanswered: Boolean = false) =
        remote.getLatestQuestions(fromDate = 1_000, page = 2, pageSize = 20, unansweredOnly = unanswered)

    @Test
    fun `latest request targets the questions endpoint with the expected parameters`() = runTest {
        server.enqueue(json("""{"items":[{"question_id":1,"title":"t","link":"l","creation_date":1}],"has_more":true}"""))

        val result = latest()

        val url = server.takeRequest().requestUrl!!
        assertEquals("/2.3/questions", url.encodedPath)
        assertEquals("1000", url.queryParameter("fromdate"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("pagesize"))
        assertEquals("desc", url.queryParameter("order"))
        assertEquals("creation", url.queryParameter("sort"))
        assertEquals("stackoverflow", url.queryParameter("site"))
        val body = (result as RemoteResult.Success).value
        assertEquals(1L, body.items.single().questionId)
        assertTrue(body.hasMore)
    }

    @Test
    fun `unanswered request targets the no-answers endpoint`() = runTest {
        server.enqueue(json("""{"items":[],"has_more":false}"""))

        latest(unanswered = true)

        assertEquals("/2.3/questions/no-answers", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `unknown json fields are ignored`() = runTest {
        server.enqueue(json("""{"items":[{"question_id":1,"title":"t","link":"l","creation_date":1,"extra":1}],"quota_max":300}"""))

        assertTrue(latest() is RemoteResult.Success)
    }

    @Test
    fun `http error becomes Http failure with the code`() = runTest {
        server.enqueue(json("""{"error_id":502,"error_name":"throttle_violation"}""", code = 400))

        val result = latest()

        assertEquals(400, (result as RemoteResult.Failure.Http).code)
    }

    @Test
    fun `body that is not json becomes Malformed`() = runTest {
        server.enqueue(json("not json"))

        assertTrue(latest() is RemoteResult.Failure.Malformed)
    }

    @Test
    fun `connection failure becomes Network`() = runTest {
        serverDown()

        assertTrue(latest() is RemoteResult.Failure.Network)
    }

    @Test
    fun `unknown host becomes Network`() = runTest {
        remote = remoteFor("http://nonexistent.invalid/")

        assertTrue(latest() is RemoteResult.Failure.Network)
    }

    @Test
    fun `getQuestion calls the id endpoint`() = runTest {
        server.enqueue(json("""{"items":[{"question_id":42,"title":"t","link":"l","creation_date":1}]}"""))

        val result = remote.getQuestion(42)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/2.3/questions/42", url.encodedPath)
        assertEquals("stackoverflow", url.queryParameter("site"))
        assertEquals(42L, (result as RemoteResult.Success).value.items.single().questionId)
    }

    @Test
    fun `getQuestion http error becomes Http failure`() = runTest {
        server.enqueue(json("{}", code = 500))

        assertEquals(500, (remote.getQuestion(1) as RemoteResult.Failure.Http).code)
    }

    @Test
    fun `getQuestion connection failure becomes Network`() = runTest {
        serverDown()

        assertTrue(remote.getQuestion(1) is RemoteResult.Failure.Network)
    }

    @Test
    fun `cancelling a slow call is not turned into a result and does not crash`() = runTest {
        // The server accepts the request and never answers, so the call is in flight until cancelled.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        var returned: RemoteResult<*>? = null

        val job = launch(Dispatchers.IO) { returned = latest() }
        server.takeRequest(5, TimeUnit.SECONDS) // the request is really in flight
        val start = System.nanoTime()
        job.cancelAndJoin()

        // The blocking read is stopped at once (connection closed), not after the 10 s read timeout.
        assertTrue("cancel took too long", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2))

        // If the cancellation had been swallowed, `returned` would be a Failure.Unexpected.
        assertNull(returned)
        assertTrue(job.isCancelled)
        // runTest also fails on any uncaught exception, so reaching this line means no crash.
    }

    @Test
    fun `a gzip body is decompressed`() = runTest {
        val gzipped = Buffer().apply {
            GZIPOutputStream(outputStream()).use {
                it.write("""{"items":[{"question_id":7,"title":"t","link":"l","creation_date":1}]}""".toByteArray())
            }
        }
        server.enqueue(json("").setHeader("Content-Encoding", "gzip").setBody(gzipped))

        assertEquals(7L, (latest() as RemoteResult.Success).value.items.single().questionId)
    }

    @Test
    fun `a body that trickles in longer than the call timeout becomes Network`() = runTest {
        // Every chunk arrives well within the read timeout, so only the total call timeout can stop it.
        remote = remoteFor(server.url("/").toString(), callTimeoutMillis = 500)
        val body = """{"items":[],"padding":"${"x".repeat(200)}"}"""
        server.enqueue(json(body).throttleBody(10, 100, TimeUnit.MILLISECONDS)) // about 2 s in total
        val start = System.nanoTime()

        val result = withContext(Dispatchers.IO) { latest() }

        assertTrue(result is RemoteResult.Failure.Network)
        assertTrue("took too long", System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(1_500))
    }
}
