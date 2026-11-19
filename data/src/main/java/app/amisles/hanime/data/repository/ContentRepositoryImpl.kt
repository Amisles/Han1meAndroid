package app.amisles.hanime.data.repository

import app.amisles.hanime.data.cache.VideoDetailStore
import app.amisles.hanime.data.preferences.Preferences
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
import app.amisles.hanime.domain.model.PlaylistInfo
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.domain.model.SubscriptionsContent
import app.amisles.hanime.domain.model.UserVideoListResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.extractVideoId
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.domain.result.DomainError
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
    private val downloadPageParser: DownloadPageParser,
    private val videoDetailStore: VideoDetailStore
) : ContentRepository {

    override suspend fun isChallengePassed(): Boolean =
        runCatching { networkService.fetchHomePageWithBaseUrl() }.isSuccess

    override suspend fun getHomeData(): AppResult<HomePageData> {
        return try {
            val result = networkService.fetchHomePageWithBaseUrl()
            AppLogger.log("ContentRepositoryImpl", "HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(homePageParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getHomeData: ${e.message}", e)
            AppResult.error(e.message.orEmpty(), e)
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
            emit(HomeDataEvent.Error(e.message.orEmpty(), e))
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
            AppResult.error(e.message.orEmpty(), e)
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
            AppResult.error(e.message.orEmpty(), e)
        }
    }


    override suspend fun getVideoDetail(videoUrl: String): AppResult<VideoDetail> {
        return try {
            val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
            AppLogger.log("ContentRepositoryImpl", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            val detail = watchPageParser.parse(result.html, result.baseUrl)
                ?: return AppResult.error(DomainError.DETAIL_PARSE_FAILED)
            // 单次解析拿到的也是最新结果：写回缓存，避免刷新的播放地址被上一次的旧缓存顶掉
            if (detail.title.isNotBlank()) {
                videoDetailStore.put(
                    videoDetailCacheKey(videoUrl),
                    detail,
                    Preferences.videoDetailCacheLimit
                )
            }
            AppResult.success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getVideoDetail: ${e.message}", e)
            AppResult.error(e.message.orEmpty(), e)
        }
    }


    /**
     * 流式加载视频详情。
     *
     * 命中常驻缓存时先整块下发缓存内容（详情页立刻可用），随后仍会静默发起一次网络请求：
     * 解析完成后与缓存做差异比较，内容确实变了才再下发一次主信息，并把新结果写回缓存。
     * 无缓存时保持「主信息 → 播放列表 → 相关视频」的渐进下发，首屏不必等整页解析。
     */
    override fun getVideoDetailStream(videoUrl: String): Flow<VideoDetailEvent> = flow {
        val cacheKey = videoDetailCacheKey(videoUrl)
        val cached = videoDetailStore.get(cacheKey)
        if (cached != null) {
            AppLogger.log("ContentRepositoryImpl", "Video detail cache hit: ${cached.title}")
            emit(VideoDetailEvent.MainInfo(cached))
        }
        try {
            val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
            AppLogger.log("ContentRepositoryImpl", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            var mainInfo: VideoDetail? = null
            var playlist: PlaylistInfo? = null
            var playlistParsed = false
            var relatedVideos: List<HanimeVideo> = emptyList()
            var relatedParsed = false
            var failed = false
            watchPageParser.parseStreaming(result.html, result.baseUrl).collect { event ->
                when (event) {
                    is VideoDetailEvent.MainInfo -> mainInfo = event.detail
                    is VideoDetailEvent.Playlist -> {
                        playlist = event.playlist
                        playlistParsed = true
                    }
                    is VideoDetailEvent.RelatedVideos -> {
                        relatedVideos = event.videos
                        relatedParsed = true
                    }
                    is VideoDetailEvent.Error -> failed = true
                }
                // 已有缓存时不逐块下发：流式的主信息不含相关视频与播放列表，
                // 直接发出去会先把界面上现有的列表清空再补回，等于白闪一下
                if (cached == null) emit(event)
            }
            val parsed = mainInfo
            if (!failed && parsed != null && playlistParsed && relatedParsed && parsed.title.isNotBlank()) {
                val fresh = parsed.copy(playlist = playlist, relatedVideos = relatedVideos)
                if (cached != null) {
                    val merged = mergeRefreshedVideoDetail(cached, fresh)
                    if (merged != cached) emit(VideoDetailEvent.MainInfo(merged))
                }
                // 新直链只落盘：界面上的播放器仍在用缓存里的直链，换成新签名会让它重载并回到片头
                videoDetailStore.put(cacheKey, fresh, Preferences.videoDetailCacheLimit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getVideoDetailStream: ${e.message}", e)
            // 命中缓存时后台刷新失败保持静默：用户手上已有完整内容，不该被一次刷新失败打断
            if (cached == null) emit(VideoDetailEvent.Error(e.message.orEmpty(), e))
        }
    }.flowOn(Dispatchers.IO)

    // 解析结果带 csrfToken / 当前用户 ID / 订阅态，按登录用户分区，换账号后不得复用
    private fun videoDetailCacheKey(videoUrl: String): String {
        val videoId = extractVideoId(videoUrl).ifEmpty { videoUrl }
        return Preferences.savedUserId + "|" + videoId
    }


    override suspend fun getDownloadQualities(videoId: String): AppResult<List<DownloadQuality>> {
        return try {
            val result = networkService.fetchDownloadPageWithBaseUrl(videoId)
            AppLogger.log("ContentRepositoryImpl", "Download page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(downloadPageParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("ContentRepositoryImpl", "Error in getDownloadQualities: ${e.message}", e)
            AppResult.error(e.message.orEmpty(), e)
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


    override suspend fun getWatchLaterPage(url: String): List<HanimeVideo> =
        networkService.fetchWatchLaterPage(url)


    override suspend fun getSubscriptionsPage(query: String): SubscriptionsContent =
        networkService.fetchSubscriptionsPage(query)


    override suspend fun getUserVideoList(authorId: String, page: Int): UserVideoListResult? =
        networkService.fetchUserVideoList(authorId, page)
}

/**
 * 把刷新结果合并到界面上正在展示的那份详情。
 *
 * 每次抓页面拿到的直链都带新的时效签名：原样下发会让正在播放的播放器重新加载、进度归零。
 * 分辨率集合没变时保留缓存里的直链，新直链只写进缓存供下次打开使用；
 * 直链真过期导致播放失败时，由详情页播放失败后的换源链路单独刷新。
 */
internal fun mergeRefreshedVideoDetail(cached: VideoDetail, fresh: VideoDetail): VideoDetail {
    val sameQualities = fresh.videoSources.map { it.resolution to it.size } ==
        cached.videoSources.map { it.resolution to it.size }
    return if (sameQualities) {
        fresh.copy(
            videoSources = cached.videoSources,
            defaultSourceUrl = cached.defaultSourceUrl
        )
    } else {
        fresh
    }
}

