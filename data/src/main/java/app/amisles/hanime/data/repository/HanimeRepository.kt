package app.amisles.hanime.data.repository

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
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.flow.Flow
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
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error adding favorite: ${e.message}", e)
        }
    }

    suspend fun removeFavorite(videoId: String) {
        try {
            favoriteDao.removeFavoriteById(videoId)
            AppLogger.log("HanimeRepository", "Favorite removed successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error removing favorite: ${e.message}", e)
        }
    }

    suspend fun isFavorite(videoId: String): Boolean {
        return try {
            favoriteDao.isFavorite(videoId)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error checking favorite: ${e.message}", e)
            false
        }
    }

    suspend fun getAllFavorites(): List<FavoriteVideo> {
        return try {
            val favorites = favoriteDao.getAllFavorites()
            AppLogger.log("HanimeRepository", "Got ${favorites.size} favorites")
            favorites
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error getting favorites: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFavoriteCount(): Int {
        return try {
            favoriteDao.getFavoriteCount()
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error getting favorite count: ${e.message}", e)
            0
        }
    }

    suspend fun addWatchHistory(history: WatchHistory) {
        try {
            watchHistoryDao.addWatchHistory(history)
            AppLogger.log("HanimeRepository", "Watch history added successfully")
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error removing watch history: ${e.message}", e)
        }
    }

    suspend fun clearWatchHistory() {
        try {
            watchHistoryDao.clearWatchHistory()
            AppLogger.log("HanimeRepository", "Watch history cleared successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error clearing watch history: ${e.message}", e)
        }
    }

    suspend fun getWatchHistoryCount(): Int {
        return try {
            watchHistoryDao.getWatchHistoryCount()
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error getting watch history count: ${e.message}", e)
            0
        }
    }
    suspend fun getHomeData(): HomePageData {
        val result = networkService.fetchHomePageWithBaseUrl()
        AppLogger.log("HanimeRepository", "HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
        return homePageParser.parse(result.html, result.baseUrl)
    }

    suspend fun searchVideos(query: String, genre: String? = null, sort: String? = null, page: Int = 1): List<HanimeVideo> {
        try {
            val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page)
            AppLogger.log("HanimeRepository", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            return searchPageParser.parse(result.html, result.baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in searchVideos: ${e.message}", e)
            return emptyList()
        }
    }

    suspend fun searchVideosWithPagination(query: String, genre: String? = null, sort: String? = null, page: Int = 1): SearchResult {
        val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page)
        AppLogger.log("HanimeRepository", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
        return searchPageParser.parseWithPagination(result.html, result.baseUrl)
    }

    suspend fun getVideoDetail(videoUrl: String): VideoDetail {
        val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
        AppLogger.log("HanimeRepository", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
        return watchPageParser.parse(result.html, result.baseUrl)
            ?: throw IllegalStateException("无法解析视频详情页")
    }

    suspend fun getDownloadQualities(videoId: String): List<DownloadQuality> {
        try {
            val result = networkService.fetchDownloadPageWithBaseUrl(videoId)
            AppLogger.log("HanimeRepository", "Download page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            return downloadPageParser.parse(result.html, result.baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getDownloadQualities: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 拉取视频评论列表。
     * 不捕获异常，让上层 ViewModel 显示颜文字错误提示。
     */
    suspend fun getComments(videoId: String): List<Comment> {
        val json = networkService.fetchComments(videoId)
        AppLogger.log("HanimeRepository", "Comments JSON received, length: ${json.length}")
        return commentParser.parse(json)
    }

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val page = try {
            networkService.fetchLoginPageWithBaseUrl()
        } catch (t: Throwable) {
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
        } catch (t: Throwable) {
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
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error adding search history: ${e.message}", e)
        }
    }

    suspend fun removeSearchHistory(query: String) {
        try {
            searchHistoryDao.removeSearch(query)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error removing search history: ${e.message}", e)
        }
    }

    suspend fun clearSearchHistory() {
        try {
            searchHistoryDao.clearAllSearch()
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error clearing search history: ${e.message}", e)
        }
    }
}