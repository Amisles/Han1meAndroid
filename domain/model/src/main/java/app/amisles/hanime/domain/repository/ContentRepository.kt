package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeDataEvent
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoDetailEvent
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * 站点内容仓储：首页 / 搜索 / 详情 / 画质。
 *
 * ## 为什么拆成四个仓储接口
 *
 * 原先只有一个具体的 `HanimeRepository`（38 个公开成员），且六个 feature 模块都直接
 * 依赖 `:data`。问题不在"类太大"，而在**依赖方向**：上层为了拿到一个"读首页"的能力，
 * 被迫依赖整个网络层、解析层与 Room 实现。
 *
 * 拆分的依据是"变化的理由"，而非方法数量：
 * - 本接口随**站点改版**而变（HTML 结构、URL 形态）；
 * - [LibraryRepository] 随**本地库表结构**而变（Room schema）；
 * - [AccountRepository] 随**登录协议**而变（Cookie、CSRF、WebView 流程）；
 * - [DownloadRepository] 随**本地文件系统与下载调度**而变。
 *
 * 四者变化频率与触发条件互不相同，合并会让任一方的改动波及全部调用方。
 *
 * ## 实现约束
 *
 * 接口刻意不暴露 OkHttp、Jsoup、Room 等任何实现细节；实现方（`:data`）可以自由替换
 * 抓取或缓存策略，而不影响上层。
 */
interface ContentRepository {
    /** 一次性拉取首页（同步语义，主要供测试与兜底使用）。 */
    suspend fun getHomeData(): AppResult<HomePageData>

    /** 流式拉取首页：按 Banner / 分区逐步推送，供首屏渐进渲染。 */
    fun getHomeDataStream(): Flow<HomeDataEvent>

    suspend fun searchVideos(
        query: String,
        genre: String? = null,
        sort: String? = null,
        page: Int = 1
    ): AppResult<List<HanimeVideo>>

    suspend fun searchVideosWithPagination(
        query: String,
        genre: String? = null,
        sort: String? = null,
        page: Int = 1,
        tags: List<String> = emptyList(),
        broad: Boolean = false,
        date: String = "",
        duration: String = ""
    ): AppResult<SearchResult>

    suspend fun getVideoDetail(videoUrl: String): AppResult<VideoDetail>

    /** 流式拉取详情：主信息 → 播放列表 → 相关视频。 */
    fun getVideoDetailStream(videoUrl: String): Flow<VideoDetailEvent>

    /** 拉取某视频的可下载画质列表。 */
    suspend fun getDownloadQualities(videoId: String): AppResult<List<DownloadQuality>>
}
