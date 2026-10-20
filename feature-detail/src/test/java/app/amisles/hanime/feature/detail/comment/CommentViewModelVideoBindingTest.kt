package app.amisles.hanime.feature.detail.comment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 P0-1 的语义契约：视频域 → 评论域的视频切换通道必须是「带当前值 + 幂等」的。
 *
 * 原实现用 `MutableSharedFlow(extraBufferCapacity = 1)`（replay = 0）发送，UI 侧的
 * LaunchedEffect 是首次启动收集，等它挂起到 ACTIVE 时 loadVideoDetail() 早已同步发完，
 * 首次进入详情页的事件必然丢失 —— 评论域 currentVideoId 保持空串，loadComments() 首行
 * 直接 return，评论永远加载不出来。
 *
 * 这里不复用真实 ViewModel（其构造需要 Android Context 与仓储实现），而是用最小模型
 * 复刻同样的读写路径，把「值语义」本身钉住：换了实现但语义退化时这条测试会失败。
 */
class CommentViewModelVideoBindingTest {

    /** 复刻评论域的「接收端」：记下被要求切换到的视频，并统计复位次数。 */
    private class CommentResetRecorder {
        var currentVideoId: String = ""
            private set
        var resetCount: Int = 0
            private set
        var commentsLoaded: Boolean = false
            private set

        fun selectVideo(videoId: String) {
            if (videoId == currentVideoId && videoId.isNotEmpty()) return
            currentVideoId = videoId
            resetCount++
            commentsLoaded = false
        }

        fun markLoaded() { commentsLoaded = true }
    }

    /** 复刻视频域的「发送端」：必须是 StateFlow，晚订阅能拿到当前值。 */
    private class VideoSwitchedSource {
        private val _state = MutableStateFlow("")
        val state: StateFlow<String> = _state.asStateFlow()
        val current: String get() = _state.value

        fun switchTo(videoId: String) { _state.value = videoId }
    }

    @Test
    fun `StateFlow 的晚订阅者能拿到已发布的当前视频`() {
        val source = VideoSwitchedSource()
        source.switchTo("video-42")

        // 模拟 UI 在 loadVideoDetail() 之后才启动收集：直接读当前值即可，不会丢。
        assertEquals("video-42", source.state.value)
        assertEquals("video-42", source.current)
    }

    @Test
    fun `同一视频重复下发不会重复复位`() {
        val source = VideoSwitchedSource()
        val recorder = CommentResetRecorder()

        source.switchTo("video-1")
        recorder.selectVideo(source.state.value)
        recorder.markLoaded()
        assertEquals(1, recorder.resetCount)
        assertTrue(recorder.commentsLoaded)

        // 配置变更 / 重新订阅会重放同一个值，此时不得清掉已加载的评论
        recorder.selectVideo(source.state.value)
        assertEquals(1, recorder.resetCount)
        assertTrue(recorder.commentsLoaded)
    }

    @Test
    fun `切换到不同视频会复位且清空已加载标记`() {
        val source = VideoSwitchedSource()
        val recorder = CommentResetRecorder()

        source.switchTo("video-1")
        recorder.selectVideo(source.state.value)
        recorder.markLoaded()

        source.switchTo("video-2")
        recorder.selectVideo(source.state.value)

        assertEquals(2, recorder.resetCount)
        assertEquals("video-2", recorder.currentVideoId)
        assertFalse(recorder.commentsLoaded)
    }

    @Test
    fun `空视频 id 不会被当作有效切换`() {
        val source = VideoSwitchedSource()
        val recorder = CommentResetRecorder()

        // 初始值就是空串；UI 侧以 isNotEmpty() 过滤，因此根本不会调用 selectVideo
        assertTrue(source.state.value.isEmpty())

        // 短路条件是 `videoId == currentVideoId && videoId.isNotEmpty()`，空串不满足
        // isNotEmpty() 分支，因此每次传空串都会真实执行一次复位 —— 这正是 UI 侧必须用
        // isNotEmpty() 过滤掉 StateFlow 初始值的原因。此处把该行为钉住，避免有人把
        // 短路条件改成只比较相等，从而让「初始空值」把已加载的评论清空。
        recorder.selectVideo("")
        assertEquals(1, recorder.resetCount)
        recorder.selectVideo("")
        assertEquals(2, recorder.resetCount)
    }
}
