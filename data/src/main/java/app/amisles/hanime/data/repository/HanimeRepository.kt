package app.amisles.hanime.data.repository

import android.database.SQLException
import app.amisles.hanime.data.local.database.FavoriteDao
import app.amisles.hanime.data.local.database.SearchHistoryDao
import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.data.parser.CommentParser
import app.amisles.hanime.data.parser.DownloadPageParser
import app.amisles.hanime.data.parser.HomePageParser
import app.amisles.hanime.data.parser.LoginParser
import app.amisles.hanime.data.parser.SearchPageParser
import app.amisles.hanime.data.parser.WatchPageParser
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HanimeRepository @Inject constructor(
    private val networkService: NetworkService,
    private val homePageParser: HomePageParser,
    private val searchPageParser: SearchPageParser,
    private val watchPageParser: WatchPageParser,
    private val downloadPageParser: DownloadPageParser,
    private val commentParser: CommentParser,
    private val favoriteDao: FavoriteDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val searchHistoryDao: SearchHistoryDao
) {

    suspend fun addFavorite(video: FavoriteVideo) {
        try {
            favoriteDao.addFavorite(video)
            AppLogger.log("HanimeRepository", "Favorite added successfully")
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error adding favorite: ${e.message}", e)
        }
    }

    suspend fun removeFavorite(videoId: String) {
        try {
            favoriteDao.removeFavoriteById(videoId)
            AppLogger.log("HanimeRepository", "Favorite removed successfully")
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error removing favorite: ${e.message}", e)
        }
    }

    suspend fun isFavorite(videoId: String): Boolean {
        return try {
            favoriteDao.isFavorite(videoId)
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error checking favorite: ${e.message}", e)
            false
        }
    }

    suspend fun getAllFavorites(): List<FavoriteVideo> {
        return try {
            val favorites = favoriteDao.getAllFavorites()
            AppLogger.log("HanimeRepository", "Got ${favorites.size} favorites")
            favorites
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error getting favorites: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFavoriteCount(): Int {
        return try {
            favoriteDao.getFavoriteCount()
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error getting favorite count: ${e.message}", e)
            0
        }
    }

    suspend fun addWatchHistory(history: WatchHistory) {
        try {
            watchHistoryDao.addWatchHistory(history)
            AppLogger.log("HanimeRepository", "Watch history added successfully")
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error adding watch history: ${e.message}", e)
        }
    }

    fun getAllWatchHistoryFlow(): Flow<List<WatchHistory>> {
        return watchHistoryDao.getAllWatchHistory()
    }

    suspend fun removeWatchHistory(videoId: String) {
        try {
            watchHistoryDao.removeWatchHistory(videoId)
            AppLogger.log("HanimeRepository", "Watch history removed successfully")
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error removing watch history: ${e.message}", e)
        }
    }

    suspend fun clearWatchHistory() {
        try {
            watchHistoryDao.clearWatchHistory()
            AppLogger.log("HanimeRepository", "Watch history cleared successfully")
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error clearing watch history: ${e.message}", e)
        }
    }

    suspend fun getWatchHistoryCount(): Int {
        return try {
            watchHistoryDao.getWatchHistoryCount()
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error getting watch history count: ${e.message}", e)
            0
        }
    }
    suspend fun getHomeData(): AppResult<HomePageData> {
        return try {
            val result = networkService.fetchHomePageWithBaseUrl()
            AppLogger.log("HanimeRepository", "HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(homePageParser.parse(result.html, result.baseUrl))
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in getHomeData: ${e.message}", e)
            AppResult.error(e.message ?: "加载首页失败", e)
        }
    }

    suspend fun searchVideos(query: String, genre: String? = null, sort: String? = null, page: Int = 1): AppResult<List<HanimeVideo>> {
        return try {
            val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page)
            AppLogger.log("HanimeRepository", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(searchPageParser.parse(result.html, result.baseUrl))
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in searchVideos: ${e.message}", e)
            AppResult.error(e.message ?: "搜索失败", e)
        }
    }

    suspend fun searchVideosWithPagination(query: String, genre: String? = null, sort: String? = null, page: Int = 1): AppResult<SearchResult> {
        return try {
            val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page)
            AppLogger.log("HanimeRepository", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(searchPageParser.parseWithPagination(result.html, result.baseUrl))
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in searchVideosWithPagination: ${e.message}", e)
            AppResult.error(e.message ?: "搜索失败", e)
        }
    }

    suspend fun getVideoDetail(videoUrl: String): AppResult<VideoDetail> {
        return try {
            val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
            AppLogger.log("HanimeRepository", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            val detail = watchPageParser.parse(result.html, result.baseUrl)
                ?: return AppResult.error("无法解析视频详情页")
            AppResult.success(detail)
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in getVideoDetail: ${e.message}", e)
            AppResult.error(e.message ?: "加载视频详情失败", e)
        }
    }

    suspend fun getDownloadQualities(videoId: String): AppResult<List<DownloadQuality>> {
        return try {
            val result = networkService.fetchDownloadPageWithBaseUrl(videoId)
            AppLogger.log("HanimeRepository", "Download page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            AppResult.success(downloadPageParser.parse(result.html, result.baseUrl))
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in getDownloadQualities: ${e.message}", e)
            AppResult.error(e.message ?: "获取下载画质失败", e)
        }
    }

    /**
     * 拉取视频评论列表。
     * 不捕获异常，让上层 ViewModel 显示颜文字错误提示。
     */
    suspend fun getComments(videoId: String): AppResult<List<Comment>> {
        return try {
            val json = networkService.fetchComments(videoId)
            AppLogger.log("HanimeRepository", "Comments JSON received, length: ${json.length}")
            AppResult.success(commentParser.parse(json))
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in getComments: ${e.message}", e)
            AppResult.error(e.message ?: "加载评论失败", e)
        }
    }

    /**
     * 拉取评论的回复列表。
     * 不捕获异常，让上层 ViewModel 处理错误。
     */
    suspend fun getReplies(commentId: String): AppResult<List<Reply>> {
        return try {
            val json = networkService.fetchReplies(commentId)
            AppLogger.log("HanimeRepository", "Replies JSON received, length: ${json.length}")
            AppResult.success(commentParser.parseReplies(json))
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in getReplies: ${e.message}", e)
            AppResult.error(e.message ?: "加载回复失败", e)
        }
    }

    /**
     * 发表视频评论。
     *
     * @param videoId 视频 ID
     * @param commentText 评论内容
     * @param commentCount 当前评论数（从 VideoDetail.commentCount 获取）
     * @param csrfToken CSRF Token（从 VideoDetail.csrfToken 获取）
     * @return Pair(新评论对象, 新评论总数)，失败抛异常
     */
    suspend fun postComment(
        videoId: String,
        commentText: String,
        commentCount: Int,
        csrfToken: String
    ): AppResult<Pair<Comment, Int>> {
        return try {
            val userId = Preferences.savedUserId.ifBlank {
                return AppResult.error("未登录，无法发表评论")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请刷新页面后重试")
            }
            val json = networkService.postComment(
                videoId = videoId,
                commentText = commentText,
                commentCount = commentCount,
                csrfToken = csrfToken,
                userId = userId
            )
            AppLogger.log("HanimeRepository", "postComment JSON received, length: ${json.length}")
            val parsed = commentParser.parsePostedComment(json)
                ?: return AppResult.error("评论发表成功但解析失败")
            AppResult.success(parsed)
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in postComment: ${e.message}", e)
            AppResult.error(e.message ?: "发表评论失败", e)
        }
    }

    /**
     * 切换评论点赞状态（点赞 / 取消点赞）。
     * 官网接口：POST /commentLike，凭 like-comment-status 区分点赞与取消。
     *
     * @param commentId 评论 ID（foreign_id）
     * @param currentLikeStatus 用户当前点赞状态：0=未点赞，1=已点赞（作为 like-comment-status 上传）
     * @param likeCount 评论当前点赞数（作为 comment-likes-count / comment-likes-sum 上传）
     * @param csrfToken CSRF Token（从 VideoDetail.csrfToken 获取）
     */
    suspend fun toggleCommentLike(
        commentId: String,
        currentLikeStatus: Int,
        likeCount: Int,
        csrfToken: String
    ): AppResult<Unit> {
        return try {
            val userId = Preferences.savedUserId.ifBlank {
                return AppResult.error("请先登录后再点赞评论")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请刷新页面后重试")
            }
            networkService.toggleCommentLike(
                commentId = commentId,
                commentLikeUserId = userId,
                currentLikeStatus = currentLikeStatus,
                likeCount = likeCount,
                csrfToken = csrfToken
            )
            AppResult.success(Unit)
        } catch (e: IOException) {
            AppLogger.logError("HanimeRepository", "Error in toggleCommentLike: ${e.message}", e)
            AppResult.error(e.message ?: "评论点赞失败", e)
        }
    }

    suspend fun login(email: String, password: String): AppResult<String> {
        return runCatching {
        val page = try {
            networkService.fetchLoginPageWithBaseUrl()
        } catch (t: IOException) {
            val msg = t.message.orEmpty()
            when {
                "resolve" in msg || "UnknownHost" in msg || "DNS" in msg ->
                    throw IllegalStateException("无法访问 Hanime1 官网（DNS 解析失败），请检查网络、开启代理/VPN，或改用 WebView 登录方式")
                t is java.net.SocketTimeoutException || "timeout" in msg ->
                    throw IllegalStateException("连接 Hanime1 官网超时，请检查网络或改用 WebView 登录")
                "SSL" in msg || "certificate" in msg ->
                    throw IllegalStateException("SSL 证书校验失败，请检查网络代理或改用 WebView 登录")
                else ->
                    throw IllegalStateException("访问登录页失败：${t.message?.take(80) ?: "未知错误"}，请改用 WebView 登录")
            }
        }
        val csrf = LoginParser.parseCsrfToken(page.html)
            ?: throw IllegalStateException("无法获取 CSRF Token，请切换到 WebView 或手动 Cookie 登录")
        val result = try {
            networkService.postLoginForm(csrf, email, password)
        } catch (t: IOException) {
            val msg = t.message.orEmpty()
            when {
                "resolve" in msg || "UnknownHost" in msg || "DNS" in msg ->
                    throw IllegalStateException("无法访问 Hanime1 官网（DNS 解析失败），请检查网络、开启代理/VPN，或改用 WebView 登录方式")
                t is java.net.SocketTimeoutException || "timeout" in msg ->
                    throw IllegalStateException("提交登录超时，请检查网络或改用 WebView 登录")
                else ->
                    throw IllegalStateException("提交登录失败：${t.message?.take(80) ?: "未知错误"}，请改用 WebView 登录")
            }
        }
        val mergedCookie = result.setCookies
            .map { it.substringBefore(';').trim() }
            .filter { it.contains('=') }
            .joinToString("; ")
        val isRedirect = result.code in 300..399
        val hasSession = mergedCookie.contains("laravel_session", ignoreCase = true) ||
                mergedCookie.contains("remember_web", ignoreCase = true) ||
                mergedCookie.contains("session", ignoreCase = true)
        if (result.code == 503) {
            throw IllegalStateException("触发了 Cloudflare 校验，请切换到 WebView 登录")
        }
        if (result.code in 200..299 && result.body != null) {
            val errorMsg = LoginParser.parseLoginFailed(result.body)
            if (errorMsg != null) throw IllegalStateException(errorMsg)
        }
        if (!isRedirect && !hasSession) {
            throw IllegalStateException("登录失败：请检查邮箱或密码，或切换到 WebView 登录")
        }
        val userId = LoginParser.parseUserIdFromSetCookies(result.setCookies)
        Preferences.saveLogin(mergedCookie, userId)
        mergedCookie
        }.fold(
            onSuccess = { AppResult.success(it) },
            onFailure = { e ->
                AppLogger.logError("HanimeRepository", "Login failed: ${e.message}", e)
                AppResult.error(e.message ?: "登录失败", e)
            }
        )
    }

    suspend fun saveLoginCookie(cookieString: String): Boolean {
        val safe = cookieString.trim().removePrefix("Cookie:").trim()
        if (safe.isBlank()) return false
        Preferences.saveLogin(safe)
        return true
    }

    fun logout() {
        Preferences.logout()
    }

    fun getSearchHistory(): kotlinx.coroutines.flow.Flow<List<app.amisles.hanime.domain.model.SearchHistoryEntity>> {
        return searchHistoryDao.getAllHistory()
    }

    suspend fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        try {
            searchHistoryDao.addSearch(
                app.amisles.hanime.domain.model.SearchHistoryEntity(
                    query = query.trim(),
                    searchedAt = System.currentTimeMillis()
                )
            )
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error adding search history: ${e.message}", e)
        }
    }

    suspend fun removeSearchHistory(query: String) {
        try {
            searchHistoryDao.removeSearch(query)
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error removing search history: ${e.message}", e)
        }
    }

    suspend fun clearSearchHistory() {
        try {
            searchHistoryDao.clearAllSearch()
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error clearing search history: ${e.message}", e)
        }
    }
}