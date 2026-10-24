package app.amisles.hanime.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.amisles.hanime.domain.model.DownloadGateReason

/**
 * 调度闸门的时间窗与原因聚合。
 *
 * 网络与充电状态依赖系统服务，只能在真机验证；这里覆盖纯逻辑部分：
 * 夜间时间窗的边界，以及多条原因同时成立时的聚合结果。
 */
class DownloadGateTest {

    @Test
    fun nightWindowIncludesStartAndExcludesEnd() {
        assertTrue(DownloadGate.isNightHour(0))
        assertTrue(DownloadGate.isNightHour(3))
        assertTrue(DownloadGate.isNightHour(5))
        assertFalse(DownloadGate.isNightHour(6))
    }

    @Test
    fun nightWindowExcludesDaytime() {
        assertFalse(DownloadGate.isNightHour(12))
        assertFalse(DownloadGate.isNightHour(23))
    }

    @Test
    fun openStateReportsAllowed() {
        val state = app.amisles.hanime.domain.model.DownloadGateState()
        assertTrue(state.allowed)
        assertFalse(state.blocked)
    }

    @Test
    fun blockedStateListsEveryReason() {
        val state = app.amisles.hanime.domain.model.DownloadGateState(
            listOf(DownloadGateReason.NEED_WIFI, DownloadGateReason.NEED_NIGHT)
        )
        assertFalse(state.allowed)
        assertTrue(state.blocked)
        assertEquals(2, state.blockedReasons.size)
    }
}
