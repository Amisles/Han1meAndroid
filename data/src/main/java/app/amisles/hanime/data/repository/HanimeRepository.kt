package app.amisles.hanime.data.repository

import android.content.Context
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.data.local.database.FavoriteDatabase
import app.amisles.hanime.data.parser.LoginParser
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.data.parser.HomePageParser
import app.amisles.hanime.data.parser.SearchPageParser
import app.amisles.hanime.data.parser.WatchPageParser
import app.amisles.hanime.data.parser.DownloadPageParser
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.flow.Flow

class HanimeRepository private constructor(
    private val networkService: NetworkService = NetworkService(),
    private val homePageParser: HomePageParser = HomePageParser(),
    private val searchPageParser: SearchPageParser = SearchPageParser(),
    private val watchPageParser: WatchPageParser = WatchPageParser(),
    private val downloadPageParser: DownloadPageParser = DownloadPageParser()
) {
    companion object {
        @Volatile
        private var INSTANCE: HanimeRepository? = null

        fun getInstance(): HanimeRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = HanimeRepository()
                INSTANCE = instance
                instance
            }
        }
    }

    private var favoriteDao: app.amisles.hanime.data.local.database.FavoriteDao? = null
    private var watchHistoryDao: app.amisles.hanime.data.local.database.WatchHistoryDao? = null
    private var searchHistoryDao: app.amisles.hanime.data.local.database.SearchHistoryDao? = null

    fun initDatabase(context: Context) {
        if (favoriteDao == null) {
            val db = FavoriteDatabase.getInstance(context)
            favoriteDao = db.favoriteDao()
            watchHistoryDao = db.watchHistoryDao()
            searchHistoryDao = db.searchHistoryDao()
        }
    }

    suspend fun addFavorite(video: FavoriteVideo) {
        AppLogger.log("HanimeRepository", "addFavorite called, videoId: ${video.id}")
        try {
            favoriteDao?.addFavorite(video)
            AppLogger.log("HanimeRepository", "Favorite added successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error adding favorite: ${e.message}", e)
        }
    }

    suspend fun removeFavorite(videoId: String) {
        AppLogger.log("HanimeRepository", "removeFavorite called, videoId: $videoId")
        try {
            favoriteDao?.removeFavoriteById(videoId)
            AppLogger.log("HanimeRepository", "Favorite removed successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error removing favorite: ${e.message}", e)
        }
    }

    suspend fun isFavorite(videoId: String): Boolean {
        return try {
            favoriteDao?.isFavorite(videoId) ?: false
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error checking favorite: ${e.message}", e)
            false
        }
    }

    suspend fun getAllFavorites(): List<FavoriteVideo> {
        AppLogger.log("HanimeRepository", "getAllFavorites called")
        return try {
            val favorites = favoriteDao?.getAllFavorites() ?: emptyList()
            AppLogger.log("HanimeRepository", "Got ${favorites.size} favorites")
            favorites
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error getting favorites: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFavoriteCount(): Int {
        return try {
            favoriteDao?.getFavoriteCount() ?: 0
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error getting favorite count: ${e.message}", e)
            0
        }
    }

    suspend fun addWatchHistory(history: WatchHistory) {
        AppLogger.log("HanimeRepository", "addWatchHistory called, videoId: ${history.id}")
        try {
            watchHistoryDao?.addWatchHistory(history)
            AppLogger.log("HanimeRepository", "Watch history added successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error adding watch history: ${e.message}", e)
        }
    }

    fun getAllWatchHistoryFlow(): Flow<List<WatchHistory>> {
        return watchHistoryDao?.getAllWatchHistory() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun removeWatchHistory(videoId: String) {
        AppLogger.log("HanimeRepository", "removeWatchHistory called, videoId: $videoId")
        try {
            watchHistoryDao?.removeWatchHistory(videoId)
            AppLogger.log("HanimeRepository", "Watch history removed successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error removing watch history: ${e.message}", e)
        }
    }

    suspend fun clearWatchHistory() {
        AppLogger.log("HanimeRepository", "clearWatchHistory called")
        try {
            watchHistoryDao?.clearWatchHistory()
            AppLogger.log("HanimeRepository", "Watch history cleared successfully")
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error clearing watch history: ${e.message}", e)
        }
    }

    suspend fun getWatchHistoryCount(): Int {
        return try {
            watchHistoryDao?.getWatchHistoryCount() ?: 0
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error getting watch history count: ${e.message}", e)
            0
        }
    }
    suspend fun getHomeData(): HomePageData {
        val startTime = System.currentTimeMillis()
        android.util.Log.i("HanimePerformance", "========== 首页数据加载开始 ==========")

        AppLogger.log("HanimeRepository", "getHomeData called")
        try {
            // 网络请求阶段
            val networkStartTime = System.currentTimeMillis()
            AppLogger.log("HanimeRepository", "Calling networkService.fetchHomePage")
            val result = networkService.fetchHomePageWithBaseUrl()
            val networkEndTime = System.currentTimeMillis()
            val networkDuration = networkEndTime - networkStartTime
            android.util.Log.i("HanimePerformance", "📡 网络请求耗时: ${networkDuration}ms (${String.format("%.2f", networkDuration / 1000.0)}s)")

            AppLogger.log("HanimeRepository", "HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")

            AppLogger.log("HanimeRepository", "Calling parser.parseHomePage")
            val data = homePageParser.parse(result.html, result.baseUrl)

            val totalEndTime = System.currentTimeMillis()
            val totalDuration = totalEndTime - startTime
            android.util.Log.i("HanimePerformance", "📊 HTML大小: ${result.html.length} bytes (${String.format("%.2f", result.html.length / 1024.0)} KB)")
            android.util.Log.i("HanimePerformance", "⏱️ 总耗时: ${totalDuration}ms (${String.format("%.2f", totalDuration / 1000.0)}s)")
            android.util.Log.i("HanimePerformance", "⚡ 解析耗时: ${totalDuration - networkDuration}ms (${String.format("%.2f", (totalDuration - networkDuration) / 1000.0)}s)")
            android.util.Log.i("HanimePerformance", "========== 首页数据加载完成 ==========")

            return data
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getHomeData: ${e.message}", e)
            android.util.Log.e("HanimePerformance", "❌ 首页数据加载失败: ${e.message}")
            return HomePageData(banner = null, sections = emptyList())
        }
    }

    suspend fun searchVideos(query: String, genre: String? = null, sort: String? = null, page: Int = 1): List<HanimeVideo> {
        AppLogger.log("HanimeRepository", "searchVideos called with query: $query")
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
        AppLogger.log("HanimeRepository", "searchVideosWithPagination called with query: $query, page: $page")
        try {
            val result = networkService.fetchSearchPageWithBaseUrl(query, genre, sort, page)
            AppLogger.log("HanimeRepository", "Search HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            return searchPageParser.parseWithPagination(result.html, result.baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in searchVideosWithPagination: ${e.message}", e)
            return SearchResult(videos = emptyList(), currentPage = page, totalPages = 1, hasNextPage = false)
        }
    }

    suspend fun getVideoDetail(videoUrl: String): VideoDetail? {
        AppLogger.log("HanimeRepository", "getVideoDetail called, videoUrl: $videoUrl")
        try {
            val result = networkService.fetchWatchPageWithBaseUrl(videoUrl)
            AppLogger.log("HanimeRepository", "Watch page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            return watchPageParser.parse(result.html, result.baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getVideoDetail: ${e.message}", e)
            return null
        }
    }

    suspend fun getDownloadQualities(videoId: String): List<DownloadQuality> {
        AppLogger.log("HanimeRepository", "getDownloadQualities called, videoId: $videoId")
        try {
            val result = networkService.fetchDownloadPageWithBaseUrl(videoId)
            AppLogger.log("HanimeRepository", "Download page HTML received, length: ${result.html.length}, baseUrl: ${result.baseUrl}")
            return downloadPageParser.parse(result.html, result.baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getDownloadQualities: ${e.message}", e)
            return emptyList()
        }
    }

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        AppLogger.log("HanimeRepository", "login called for email prefix=${email.take(6)}")
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
        AppLogger.log("HanimeRepository", "saveLoginCookie called, length=${cookieString.length}")
        val safe = cookieString.trim().removePrefix("Cookie:").trim()
        if (safe.isBlank()) return false
        Preferences.saveLogin(safe)
        return true
    }

    fun logout() {
        AppLogger.log("HanimeRepository", "logout called")
        Preferences.logout()
    }

    fun getSearchHistory(): kotlinx.coroutines.flow.Flow<List<app.amisles.hanime.domain.model.SearchHistoryEntity>> {
        return searchHistoryDao?.getAllHistory() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        try {
            searchHistoryDao?.addSearch(
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
            searchHistoryDao?.removeSearch(query)
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error removing search history: ${e.message}", e)
        }
    }

    suspend fun clearSearchHistory() {
        try {
            searchHistoryDao?.clearAllSearch()
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error clearing search history: ${e.message}", e)
        }
    }
}