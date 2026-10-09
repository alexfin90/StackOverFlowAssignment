package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.remote.OwnerDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.domain.SdkError
import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The pager against a scripted data source: no HTTP involved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuestionsPagerImplTest {

    private class ScriptedRemote : QuestionsRemoteDataSource {
        data class Call(
            val fromDate: Long, val page: Int, val pageSize: Int, val unansweredOnly: Boolean, val toDate: Long?,
        )

        val calls = mutableListOf<Call>()

        /** Answer for each call; replace it to script a test. */
        var onCall: suspend (Call) -> RemoteResult<QuestionsResponseDto> =
            { RemoteResult.Success(QuestionsResponseDto()) }

        override suspend fun getLatestQuestions(
            fromDate: Long,
            page: Int,
            pageSize: Int,
            unansweredOnly: Boolean,
            toDate: Long?,
        ): RemoteResult<QuestionsResponseDto> {
            val call = Call(fromDate, page, pageSize, unansweredOnly, toDate)
            calls += call
            return onCall(call)
        }

        override suspend fun getQuestion(id: Long): RemoteResult<QuestionsResponseDto> = error("not used")
    }

    private val remote = ScriptedRemote()
    private var now = 1_700_000_000L

    private fun pager(unansweredOnly: Boolean = false) = QuestionsPagerImpl(
        remote = remote,
        unansweredOnly = unansweredOnly,
        pageSize = 3,
        lastDays = 30,
        nowEpochSeconds = { now },
    )

    private fun dto(id: Long) = QuestionDto(id, "Q$id", "https://so/$id", 1_699_999_000, owner = OwnerDto("Ann"))

    private fun page(vararg ids: Long, hasMore: Boolean) =
        RemoteResult.Success(QuestionsResponseDto(ids.map { dto(it) }, hasMore))

    private fun QuestionsPagerImpl.ids() = state.value.questions.map { it.id }

    @Test
    fun `starts empty and idle`() {
        val state = pager().state.value

        assertTrue(state.questions.isEmpty())
        assertFalse(state.hasMore)
        assertSame(PagerStatus.Idle, state.status)
        assertFalse(state.isLoaded)
    }

    @Test
    fun `refresh loads the first page`() = runTest {
        remote.onCall = { page(10, 9, 8, hasMore = true) }
        val pager = pager()

        pager.refresh()

        assertEquals(listOf(10L, 9L, 8L), pager.ids())
        assertTrue(pager.state.value.hasMore)
        assertSame(PagerStatus.Idle, pager.state.value.status)
        assertEquals(1, remote.calls.single().page)
    }

    @Test
    fun `status is Refreshing while the first page is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.onCall = { gate.await(); page(1, hasMore = false) }
        val pager = pager()

        val job = launch { pager.refresh() }
        runCurrent()
        assertSame(PagerStatus.Refreshing, pager.state.value.status)

        gate.complete(Unit)
        job.join()
        assertSame(PagerStatus.Idle, pager.state.value.status)
    }

    @Test
    fun `loadMore adds the next page`() = runTest {
        remote.onCall = { call ->
            if (call.page == 1) page(10, 9, 8, hasMore = true) else page(7, 6, 5, hasMore = false)
        }
        val pager = pager()
        pager.refresh()

        pager.loadMore()

        assertEquals(listOf(10L, 9L, 8L, 7L, 6L, 5L), pager.ids())
        assertFalse(pager.state.value.hasMore)
        assertEquals(listOf(1, 2), remote.calls.map { it.page })
    }

    @Test
    fun `loadMore does nothing without a next page`() = runTest {
        remote.onCall = { page(1, hasMore = false) }
        val pager = pager()
        pager.refresh()

        pager.loadMore()

        assertEquals(1, remote.calls.size)
    }

    @Test
    fun `loadMore before the first refresh does nothing`() = runTest {
        val pager = pager()

        pager.loadMore()

        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun `a repeated question is kept once`() = runTest {
        remote.onCall = { call ->
            if (call.page == 1) page(10, 9, 8, hasMore = true) else page(8, 7, 6, hasMore = false)
        }
        val pager = pager()
        pager.refresh()

        pager.loadMore()

        assertEquals(listOf(10L, 9L, 8L, 7L, 6L), pager.ids())
    }

    @Test
    fun `concurrent loadMore calls make a single request and the extra ones return at once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.onCall = { call ->
            if (call.page == 1) {
                page(1, 2, 3, hasMore = true)
            } else {
                gate.await()
                page(4, 5, 6, hasMore = false)
            }
        }
        val pager = pager()
        pager.refresh()

        val first = launch { pager.loadMore() }
        val second = launch { pager.loadMore() }
        val third = launch { pager.loadMore() }
        runCurrent()
        assertSame(PagerStatus.LoadingMore, pager.state.value.status)
        // The extra calls return at once: they are ignored, they do not queue behind the first.
        assertFalse(first.isCompleted)
        assertTrue(second.isCompleted)
        assertTrue(third.isCompleted)
        gate.complete(Unit)
        advanceUntilIdle()
        first.join()

        assertEquals(2, remote.calls.size) // page 1 and one page 2
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), pager.ids())
    }

    @Test
    fun `refresh while loadMore is running waits and then replaces the list`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var refreshes = 0
        remote.onCall = { call ->
            when {
                call.page == 2 -> { gate.await(); page(4, 5, 6, hasMore = false) }
                refreshes++ == 0 -> page(1, 2, 3, hasMore = true)
                else -> page(100, 101, hasMore = false)
            }
        }
        val pager = pager()
        pager.refresh()
        val more = launch { pager.loadMore() }
        runCurrent()

        val refresh = launch { pager.refresh() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        more.join(); refresh.join()

        assertEquals(listOf(100L, 101L), pager.ids())
        assertSame(PagerStatus.Idle, pager.state.value.status)
    }

    @Test
    fun `network failure on the first page is reported and can be retried`() = runTest {
        remote.onCall = { RemoteResult.Failure.Network("offline") }
        val pager = pager()

        pager.refresh()

        val status = pager.state.value.status as PagerStatus.Failed
        assertTrue(status.error is SdkError.Network)
        assertTrue(pager.ids().isEmpty())

        remote.onCall = { page(1, 2, hasMore = false) }
        pager.refresh()
        assertSame(PagerStatus.Idle, pager.state.value.status)
        assertEquals(listOf(1L, 2L), pager.ids())
    }

    @Test
    fun `server and parsing failures are reported as Api errors`() = runTest {
        for (failure in listOf(
            RemoteResult.Failure.Http(500, "boom"),
            RemoteResult.Failure.Malformed("bad json"),
            RemoteResult.Failure.Unexpected("bug"),
        )) {
            remote.onCall = { failure }
            val pager = pager()

            pager.refresh()

            assertTrue((pager.state.value.status as PagerStatus.Failed).error is SdkError.Api)
        }
    }

    @Test
    fun `a failed loadMore keeps the list and the same page is tried again`() = runTest {
        var failNext = true
        remote.onCall = { call ->
            when {
                call.page == 1 -> page(1, 2, 3, hasMore = true)
                failNext -> RemoteResult.Failure.Network("offline")
                else -> page(4, 5, 6, hasMore = false)
            }
        }
        val pager = pager()
        pager.refresh()

        pager.loadMore()
        assertEquals(listOf(1L, 2L, 3L), pager.ids())
        assertTrue(pager.state.value.status is PagerStatus.Failed)
        assertTrue(pager.state.value.hasMore)

        failNext = false
        pager.loadMore()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), pager.ids())
        assertEquals(listOf(1, 2, 2), remote.calls.map { it.page }) // page 2 asked twice
    }

    @Test
    fun `a failed refresh keeps the previous questions`() = runTest {
        remote.onCall = { page(1, 2, hasMore = true) }
        val pager = pager()
        pager.refresh()

        remote.onCall = { RemoteResult.Failure.Network("offline") }
        pager.refresh()

        assertEquals(listOf(1L, 2L), pager.ids())
        assertTrue(pager.state.value.status is PagerStatus.Failed)
    }

    @Test
    fun `cancelling a request restores the state and nothing is returned`() = runTest {
        remote.onCall = { call ->
            if (call.page == 1) page(1, 2, 3, hasMore = true) else CompletableDeferred<Nothing>().await()
        }
        val pager = pager()
        pager.refresh()

        val job = launch { pager.loadMore() }
        runCurrent()
        assertSame(PagerStatus.LoadingMore, pager.state.value.status)
        job.cancelAndJoin()

        assertSame(PagerStatus.Idle, pager.state.value.status)
        assertEquals(listOf(1L, 2L, 3L), pager.ids())

        // and the lock was released: another loadMore can run
        remote.onCall = { page(4, hasMore = false) }
        pager.loadMore()
        assertEquals(listOf(1L, 2L, 3L, 4L), pager.ids())
    }

    @Test
    fun `every page of a snapshot uses the same window even if the clock moves`() = runTest {
        remote.onCall = { call -> if (call.page == 1) page(1, hasMore = true) else page(2, hasMore = false) }
        val pager = pager()
        pager.refresh()
        now += 3_600 // an hour passes while the user scrolls

        pager.loadMore()

        val (first, second) = remote.calls
        assertEquals(now - 3_600, first.toDate)
        assertEquals(first.toDate, second.toDate)
        assertEquals(first.fromDate, second.fromDate)
        assertEquals(first.toDate!! - 30 * 86_400L, first.fromDate)
    }

    @Test
    fun `refresh takes a new snapshot`() = runTest {
        val pager = pager()
        pager.refresh()
        now += 3_600

        pager.refresh()

        assertEquals(now, remote.calls.last().toDate)
        assertEquals(1, remote.calls.last().page)
    }

    @Test
    fun `the filter and the page size are forwarded`() = runTest {
        val pager = pager(unansweredOnly = true)

        pager.refresh()

        assertTrue(remote.calls.single().unansweredOnly)
        assertEquals(3, remote.calls.single().pageSize)
    }

    @Test
    fun `an empty result is told apart from nothing loaded yet`() = runTest {
        remote.onCall = { page(hasMore = false) }
        val pager = pager()
        assertFalse(pager.state.value.isLoaded)

        pager.refresh()

        assertTrue(pager.state.value.questions.isEmpty())
        assertTrue(pager.state.value.isLoaded)
    }

    @Test
    fun `a failure before any success leaves isLoaded false`() = runTest {
        remote.onCall = { RemoteResult.Failure.Network("offline") }
        val pager = pager()

        pager.refresh()

        assertFalse(pager.state.value.isLoaded)
    }
}
