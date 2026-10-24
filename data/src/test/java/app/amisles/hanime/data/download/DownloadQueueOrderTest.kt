package app.amisles.hanime.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 队列排序：自定义顺序优先，未列出的按 id 升序排在后面。
 *
 * 只验证纯排序逻辑；任务启动与槽位分配依赖并发状态，只能在真机观察。
 */
class DownloadQueueOrderTest {

    private data class Item(val id: Int)

    private fun pick(order: List<Int>, ids: List<Int>): Int? =
        ids.map(::Item).minWithOrNull(
            compareBy({ order.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }, { it.id })
        )?.id

    @Test
    fun listedOrderWins() {
        assertEquals(3, pick(listOf(3, 1), listOf(1, 2, 3)))
    }

    @Test
    fun unlistedSortedByIdAscending() {
        assertEquals(2, pick(listOf(9), listOf(5, 2, 7)))
    }

    @Test
    fun listedBeatsUnlistedRegardlessOfId() {
        assertEquals(8, pick(listOf(8), listOf(1, 2, 8)))
    }

    @Test
    fun emptyOrderFallsBackToId() {
        assertEquals(1, pick(emptyList(), listOf(3, 1, 2)))
    }

    @Test
    fun emptyCandidatesYieldNull() {
        assertEquals(null, pick(listOf(1), emptyList()))
    }
}
