package com.alexfin90.stackoverflowsdk.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * Stack Exchange API v2.3 over the platform's `HttpURLConnection`: no HTTP library, so nothing can
 * clash with the host app's versions. On Android it is backed by a private, repackaged copy of
 * OkHttp that belongs to the platform.
 *
 * `order`, `sort` and `site` are fixed on purpose: they define what "latest StackOverflow
 * questions" means and are not exposed to SDK users.
 *
 * The only class that knows the transport: exceptions become [RemoteResult] here.
 */
internal class UrlConnectionQuestionsRemoteDataSource(
    private val baseUrl: String,
    private val json: Json,
    private val connectTimeoutMillis: Int,
    private val callTimeoutMillis: Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : QuestionsRemoteDataSource {

    override suspend fun getLatestQuestions(
        fromDate: Long,
        page: Int,
        pageSize: Int,
        unansweredOnly: Boolean,
    ): RemoteResult<QuestionsResponseDto> = get(
        // Same parameters for both: no-answers is filtered on the server.
        path = if (unansweredOnly) "2.3/questions/no-answers" else "2.3/questions",
        query = mapOf(
            "fromdate" to fromDate.toString(), // Unix time in seconds
            "page" to page.toString(),
            "pagesize" to pageSize.toString(),
            "order" to "desc",
            "sort" to "creation",
            "site" to SITE,
        ),
    )

    override suspend fun getQuestion(id: Long): RemoteResult<QuestionsResponseDto> =
        get(path = "2.3/questions/$id", query = mapOf("site" to SITE))

    private suspend fun get(path: String, query: Map<String, String>): RemoteResult<QuestionsResponseDto> {
        val queryString = query.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val url = URL("$baseUrl$path?$queryString")
        // HttpURLConnection blocks the thread, so the SDK moves to IO itself: callers stay main-safe.
        // The timeout runs there too, so it measures real time even when the caller's dispatcher does not.
        return withContext(io) {
            // Hard cap for the whole call, like OkHttp's callTimeout: readTimeout only limits each single read.
            withTimeoutOrNull(callTimeoutMillis) { fetch(url) }
                ?: RemoteResult.Failure.Network("Call timed out after $callTimeoutMillis ms")
        }
    }

    /**
     * A blocking read ignores coroutine cancellation, and closing the connection may itself block
     * (a plain JVM drains a small remaining body to reuse the connection). So the read runs outside
     * the caller's job: a cancelled caller stops waiting at once, and the read ends in the background
     * (closed connection, or at worst the read timeout) with its result discarded.
     */
    private suspend fun fetch(url: URL): RemoteResult<QuestionsResponseDto> {
        val connection = url.openConnection() as HttpURLConnection
        val call = transport.async { read(connection) }
        try {
            return call.await()
        } catch (e: CancellationException) {
            transport.launch { connection.disconnect() }
            throw e
        }
    }

    /** Blocking, no suspension point: cancellation is handled by [fetch], not here. */
    private fun read(connection: HttpURLConnection): RemoteResult<QuestionsResponseDto> =
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = callTimeoutMillis.toInt()
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode // sends the request and reads the status line
            if (code !in 200..299) {
                connection.errorStream?.close()
                RemoteResult.Failure.Http(code, connection.responseMessage.orEmpty())
            } else {
                // Android decompresses transparently and removes the header; a plain JVM does not.
                val stream = connection.inputStream.let {
                    if (connection.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(it) else it
                }
                val body = stream.bufferedReader().use { it.readText() }
                RemoteResult.Success(json.decodeFromString<QuestionsResponseDto>(body))
            }
        } catch (e: IOException) {
            // Also covers UnknownHostException, ConnectException and SocketTimeoutException
            // (all subclasses): offline, DNS failure, connect/read timeout.
            RemoteResult.Failure.Network(e.message)
        } catch (e: SerializationException) {
            RemoteResult.Failure.Malformed(e.message)
        } catch (e: Exception) {
            // Last line of defence: an unexpected bug must not crash the host app.
            RemoteResult.Failure.Unexpected(e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }

    /** Where blocking reads run. Not tied to any caller, so a cancelled caller never waits for them. */
    private val transport = CoroutineScope(SupervisorJob() + io)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val SITE = "stackoverflow"
    }
}
