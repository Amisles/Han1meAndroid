package app.amisles.hanime.data.repository

import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.data.parser.DownloadPageParser
import app.amisles.hanime.data.parser.HomePageParser
import app.amisles.hanime.data.parser.SearchPageParser
import app.amisles.hanime.data.parser.WatchPageParser
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeDataEvent
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.VideoDetailEvent
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.domain.model.AuthorPageDataEvent
import app.amisles.hanime.domain.model.PlaylistDetail
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.domain.model.SubscriptionsContent
import app.amisles.hanime.domain.model.UserVideoListResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import app.amisles.hanime.domain.repository.ContentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val networkService: NetworkService,
    private val homePageParser: HomePageParser,
    private val searchPageParser: SearchPageParser,
    private val watchPageParser: WatchPageParser,
    private val downloadPageParser: DownloadPageParser
) : ContentRepository {

    override suspend fun getHomeData(): AppResult<HomePageData> {
        return try {
            val result = networkService.fetchHomePageWithBaseUrl()
            AppLogger.log("ContentRepositoryImpl", "HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(homePageParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getHomeData: ${e.message}", e)
            AppResult.error(e.message ?: "加载首页失败", e)
        }
    }


    /** 流式加载首页 */
    override fun getHomeDataStream(): Flow<HomeDataEvent> = flow {
        try {
            val result = networkService.fetchHomePageWithBaseUrl()
            AppLogger.log("ContentRepositoryImpl", "HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            emitAll(homePageParser.parseStreaming(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            // 收集方取消时必须原样抛出：Exception 是 CancellationException 的父类，
            // 若在此吞掉，取消无法上抛，页面销毁后仍会向 UI 派发 Error 事件
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getHomeDataStream: ${e.message}", e)
            emit(HomeDataEvent.Error(e.message ?: "加载首页失败"))
        }
    }.flowOn(Dispatchers.IO)


    override suspend fun searchVideos(query: String, genre: String?, sort: String?, page: Int): AppResult<List<HanimeVideo>> {
        return try {
            val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page)
            AppLogger.log("ContentRepositoryImpl", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(searchPageParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in searchVideos: ${e.message}", e)
            AppResult.error(e.message ?: "搜索失败", e)
        }
    }


    override suspend fun searchVideosWithPagination(
        query: String,
        genre: String?,
        sort: String?,
        page: Int,
        tags: List<String>,
        broad: Boolean,
        date: String,
        duration: String
    ): AppResult<SearchResult> {
        return try {
            val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page, tags, broad, date, duration)
            AppLogger.log("ContentRepositoryImpl", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(searchPageParser.parseWithPagination(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in searchVideosWithPagination: ${e.message}", e)
            AppResult.error(e.message ?: "搜索失败", e)
        }
    }


    override suspend fun getVideoDetail(videoUrl: String): AppResult<VideoDetail> {
        return try {
            val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
            AppLogger.log("ContentRepositoryImpl", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            val detail = watchPageParser.parse(result.html, result.baseUrl)
                ?: return AppResult.error("无法解析视频详情页")
            AppResult.success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getVideoDetail: ${e.message}", e)
            AppResult.error(e.message ?: "加载视频详情失败", e)
        }
    }


    /**
     * 流式加载视频详情：在 IO 调度器上取回整页 HTML 后交给解析器的流式解析，
     * 按「主信息 → 播放列表 → 相关视频」顺序 emit [VideoDetailEvent]，供 ViewModel 增量刷新 UI。
     * 网络或解析失败统一转换为 [VideoDetailEvent.Error] 事件。
     */
    override fun getVideoDetailStream(videoUrl: String): Flow<VideoDetailEvent> = flow {
        try {
            val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
            AppLogger.log("ContentRepositoryImpl", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            emitAll(watchPageParser.parseStreaming(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getVideoDetailStream: ${e.message}", e)
            emit(VideoDetailEvent.Error(e.message ?: "加载视频详情失败"))
        }
    }.flowOn(Dispatchers.IO)


    override suspend fun getDownloadQualities(videoId: String): AppResult<List<DownloadQuality>> {
        return try {
            val result = networkService.fetchDownloadPageWithBaseUrl(videoId)
            AppLogger.log("ContentRepositoryImpl", "Download page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(downloadPageParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getDownloadQualities: ${e.message}", e)
            AppResult.error(e.message ?: "获取下载画质失败", e)
        }
    }


    override fun getAuthorPageStream(authorPageUrl: String): Flow<AuthorPageDataEvent> =
        networkService.fetchAuthorPageStream(authorPageUrl)


    override suspend fun getVideoListPage(url: String): List<HanimeVideo> =
        networkService.fetchVideoListPage(url)


    override suspend fun getPlaylistListPage(url: String): List<PlaylistSummary> =
        networkService.fetchPlaylistListPage(url)


    override suspend fun getPlaylistDetail(url: String): PlaylistDetail? =
        networkService.fetchPlaylistDetailPage(url)


    override suspend fun getSubscriptionsPage(query: String): SubscriptionsContent =
        networkService.fetchSubscriptionsPage(query)


    override suspend fun getUserVideoList(authorId: String, page: Int): UserVideoListResult? =
        networkService.fetchUserVideoList(authorId, page)

}
