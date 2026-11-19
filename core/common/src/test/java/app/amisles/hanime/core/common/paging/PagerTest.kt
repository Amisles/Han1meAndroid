package app.amisles.hanime.core.common.paging

import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerTest {

    private fun scope() = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `reload replaces items and records the first page`() = runBlocking {
        val pager = Pager(
            scope = scope(),
            fetch = { page -> AppResult.Success(Paged(listOf(page, page + 10), true)) },
            describeError = { "错误" }
        )

        pager.reload()

        assertEquals(listOf(1, 11), pager.items.value)
        assertEquals(1, pager.currentPage.value)
        assertTrue(pager.hasMore.value)
        assertFalse(pager.isRefreshing.value)
        assertNull(pager.error.value)
    }

    @Test
    fun `loadMore appends through merge and advances the cursor`() = runBlocking {
        val pager = Pager(
            scope = scope(),
            fetch = { page -> AppResult.Success(Paged(listOf(page), true)) },
            describeError = { "错误" }
        )

        pager.reload()
        pager.loadMore()
        pager.loadMore()

        assertEquals(listOf(1, 2, 3), pager.items.value)
        assertEquals(3, pager.currentPage.value)
        assertFalse(pager.isLoadingMore.value)
    }

    @Test
    fun `loadMore is ignored when there is no next page`() = runBlocking {
        var calls = 0
        val pager = Pager(
            scope = scope(),
            fetch = { page ->
                calls++
                AppResult.Success(Paged(listOf(page), false))
            },
            describeError = { "错误" }
        )

        pager.reload()
        pager.loadMore()

        assertEquals(1, calls)
        assertFalse(pager.hasMore.value)
    }

    @Test
    fun `empty page stops further loading even if the site claims a next page`() = runBlocking {
        val pager = Pager<Int>(
            scope = scope(),
            fetch = { AppResult.Success(Paged(emptyList(), true)) },
            describeError = { "错误" }
        )

        pager.reload()

        assertFalse(pager.hasMore.value)
    }

    @Test
    fun `error surfaces the mapped message and keeps the list`() = runBlocking {
        var fail = true
        val pager = Pager(
            scope = scope(),
            fetch = {
                if (fail) {
                    AppResult.Error("boom", IllegalStateException("boom"))
                } else {
                    AppResult.Success(Paged(listOf(1), false))
                }
            },
            describeError = { throwable -> throwable?.message ?: "兜底" }
        )

        pager.reload()
        assertEquals("boom", pager.error.value)
        assertEquals(emptyList<Int>(), pager.items.value)

        fail = false
        pager.reload()
        assertNull(pager.error.value)
        assertEquals(listOf(1), pager.items.value)
    }

    @Test
    fun `results from a superseded request are dropped`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val pager = Pager(
            scope = scope(),
            fetch = { page ->
                if (first) {
                    first = false
                    gate.await()
                    AppResult.Success(Paged(listOf(99), false))
                } else {
                    AppResult.Success(Paged(listOf(page), false))
                }
            },
            describeError = { "错误" }
        )

        pager.reload()
        pager.reload()
        gate.complete(Unit)
        yield()

        assertEquals(listOf(1), pager.items.value)
        assertEquals(1, pager.currentPage.value)
    }
}
