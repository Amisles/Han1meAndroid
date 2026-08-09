package app.amisles.hanime.data.remote

import android.util.Log
import app.amisles.hanime.data.cookie.HCookieJar
import app.amisles.hanime.data.parser.AuthorPageParser
import app.amisles.hanime.data.parser.PlaylistParser
import app.amisles.hanime.data.preferences.Preferences
import javax.inject.Inject
import javax.inject.Singleton
import app.amisles.hanime.domain.model.AuthorPageData
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistDetail
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Singleton
class NetworkService @Inject constructor(
    private val authorPageParser: AuthorPageParser,
    private val playlistParser: PlaylistParser
) {
    // 入口拦截器：非官方域名首次请求前自动探测 /enter 获取入口 cookie
    private val entryInterceptor = EntryInterceptor()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(HCookieJar)
        .addInterceptor(entryInterceptor)
        .build()

    private val noRedirectClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    // 从用户配置中获取地址
    private fun getCurrentBaseUrl(): String {
        return Preferences.baseUrl
    }

    private val uaString
        get() = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private fun buildRequest(url: String): Request {
        AppLogger.log("NetworkService", "Building request for URL: $url")
        return Request.Builder()
            .url(url)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", getCurrentBaseUrl() + "/")
            .get()
            .build()
    }

    private fun executeRequest(request: Request): String {
        return client.newCall(request).execute().use { response ->
            AppLogger.log("NetworkService", "Response code: ${response.code}")
            if (!response.isSuccessful) {
                throw Exception("Request failed with code ${response.code}")
            }
            val body = response.body?.string()
            AppLogger.log("NetworkService", "Response body length: ${body?.length ?: 0}")
            body ?: throw Exception("Empty response body")
        }
    }

    data class LoginFormResult(val code: Int, val setCookies: List<String>, val body: String?)

    suspend fun fetchLoginPageWithBaseUrl(): FetchResult {
        AppLogger.log("NetworkService", "fetchLoginPageWithBaseUrl called")
        val base = getCurrentBaseUrl()
        val url = "$base/login"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        val code = resp.code
        AppLogger.log("NetworkService", "fetchLoginPage code=$code url=$base")
        if (code in 200..399) {
            val html = resp.body?.string().orEmpty()
            return FetchResult(html, base)
        } else {
            throw Exception("login page HTTP $code")
        }
    }

    suspend fun postLoginForm(
        csrfToken: String,
        email: String,
        password: String
    ): LoginFormResult {
        AppLogger.log("NetworkService", "postLoginForm called (email length=${email.length})")
        val base = getCurrentBaseUrl()
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("email", email)
            .add("password", password)
            .build()
        val req = Request.Builder()
            .url("$base/login")
            .post(form)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "$base/login")
            .header("Origin", base)
            .header("X-CSRF-TOKEN", csrfToken)
            .build()
        noRedirectClient.newCall(req).execute().use { resp ->
            val code = resp.code
            val cookies = resp.headers("Set-Cookie")
            var body: String? = null
            if (code in 200..299) {
                body = resp.body?.string()
            }
            AppLogger.log("NetworkService", "postLoginForm code=$code setCookies=${cookies.size} base=$base")
            return LoginFormResult(code, cookies, body)
        }
    }

    suspend fun fetchHomePageWithBaseUrl(): FetchResult {
        AppLogger.log("NetworkService", "fetchHomePageWithBaseUrl called")
        val baseUrl = getCurrentBaseUrl()
        AppLogger.log("NetworkService", "Using base URL: $baseUrl")
        val url = "$baseUrl/"
        val html = executeRequest(buildRequest(url))
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchSearchPageWithBaseUrl(query: String, genre: String? = null, sort: String? = null, page: Int = 1): FetchResult {
        AppLogger.log("NetworkService", "fetchSearchPageWithBaseUrl called")
        val baseUrl = getCurrentBaseUrl()
        AppLogger.log("NetworkService", "Using base URL: $baseUrl")
        val url = buildString {
            append("$baseUrl/search?")

            if (query.isNotEmpty()) {
                append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
            }

            if (genre != null && genre.isNotEmpty()) {
                if (query.isNotEmpty()) append("&")
                append("genre=").append(URLEncoder.encode(genre, StandardCharsets.UTF_8))
            }

            if (sort != null && sort.isNotEmpty()) {
                if (query.isNotEmpty() || (genre != null && genre.isNotEmpty())) append("&")
                append("sort=").append(URLEncoder.encode(sort, StandardCharsets.UTF_8))
            }

            if (page > 1) {
                if (query.isNotEmpty() || (genre != null && genre.isNotEmpty()) || (sort != null && sort.isNotEmpty())) append("&")
                append("page=").append(page)
            }
        }
        Log.i("NetworkService", "Search API URL: $url")
        val html = executeRequest(buildRequest(url))
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchWatchPageWithBaseUrl(videoUrl: String): FetchResult {
        AppLogger.log("NetworkService", "fetchWatchPageWithBaseUrl called, videoUrl: $videoUrl")
        val url = if (videoUrl.startsWith("http")) videoUrl else "${getCurrentBaseUrl()}$videoUrl"
        AppLogger.log("NetworkService", "Full URL: $url")
        val html = executeRequest(buildRequest(url))
        val baseUrl = "https://${Regex("https?://([^/]+)").find(url)?.groupValues?.get(1) ?: Preferences.DEFAULT_BASE_URL.removePrefix("https://")}"
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchDownloadPageWithBaseUrl(videoId: String): FetchResult {
        AppLogger.log("NetworkService", "fetchDownloadPageWithBaseUrl called, videoId: $videoId")
        val baseUrl = getCurrentBaseUrl()
        AppLogger.log("NetworkService", "Fetching download page from: $baseUrl")
        val url = "$baseUrl/download?v=$videoId"
        val html = executeRequest(buildRequest(url))
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchAuthorPage(authorPageUrl: String): AuthorPageData {
        AppLogger.log("NetworkService", "fetchAuthorPage called, url: $authorPageUrl")
        return withContext(Dispatchers.IO) {
            val html = executeRequest(buildRequest(authorPageUrl))
            authorPageParser.parse(html, authorPageUrl)
                ?: throw IllegalStateException("无法解析作者主页")
        }
    }

    suspend fun fetchVideoListPage(url: String): List<HanimeVideo> {
        AppLogger.log("NetworkService", "fetchVideoListPage called, url: $url")
        return withContext(Dispatchers.IO) {
            try {
                val html = executeRequest(buildRequest(url))
                authorPageParser.parseVideoListPage(html, url)
            } catch (e: Exception) {
                AppLogger.logError("NetworkService", "Failed to fetch video list page: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun fetchPlaylistListPage(url: String): List<PlaylistSummary> {
        AppLogger.log("NetworkService", "fetchPlaylistListPage called, url: $url")
        return withContext(Dispatchers.IO) {
            try {
                val html = executeRequest(buildRequest(url))
                playlistParser.parseListPage(html, url)
            } catch (e: Exception) {
                AppLogger.logError("NetworkService", "Failed to fetch playlist list page: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun fetchPlaylistDetailPage(url: String): PlaylistDetail? {
        AppLogger.log("NetworkService", "fetchPlaylistDetailPage called, url: $url")
        return withContext(Dispatchers.IO) {
            try {
                val html = executeRequest(buildRequest(url))
                playlistParser.parseDetailPage(html, url)
            } catch (e: Exception) {
                AppLogger.logError("NetworkService", "Failed to fetch playlist detail page: ${e.message}", e)
                null
            }
        }
    }

    suspend fun fetchUserVideoList(authorId: String, page: Int = 1): app.amisles.hanime.domain.model.UserVideoListResult? {
        AppLogger.log("NetworkService", "fetchUserVideoList called, authorId: $authorId, page: $page")
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = getCurrentBaseUrl()
                val url = if (page > 1) {
                    "$baseUrl/user/$authorId/uploaded?page=$page"
                } else {
                    "$baseUrl/user/$authorId/uploaded"
                }
                AppLogger.log("NetworkService", "Fetching user video list from: $url")
                val html = executeRequest(buildRequest(url))
                authorPageParser.parseUserVideoList(html, url)
            } catch (e: Exception) {
                AppLogger.logError("NetworkService", "Failed to fetch user video list: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 拉取视频评论 JSON。
     *
     * 官网接口：GET /loadComment?id={videoId}&type=video&content=comment-tablink
     * 返回 JSON：{"comments": "<HTML>", "content": "comment-tablink"}
     */
    suspend fun fetchComments(videoId: String): String {
        AppLogger.log("NetworkService", "fetchComments called, videoId: $videoId")
        return withContext(Dispatchers.IO) {
            val baseUrl = getCurrentBaseUrl()
            val url = "$baseUrl/loadComment?id=$videoId&type=video&content=comment-tablink"
            AppLogger.log("NetworkService", "Fetching comments from: $url")
            executeRequest(buildRequest(url))
        }
    }

    /**
     * 拉取评论回复 JSON。
     *
     * 官网接口：GET /loadReplies?id={commentId}
     * 返回 JSON：{"comment_id": "commentId", "replies": "<HTML>"}
     */
    suspend fun fetchReplies(commentId: String): String {
        AppLogger.log("NetworkService", "fetchReplies called, commentId: $commentId")
        return withContext(Dispatchers.IO) {
            val baseUrl = getCurrentBaseUrl()
            val url = "$baseUrl/loadReplies?id=$commentId"
            AppLogger.log("NetworkService", "Fetching replies from: $url")
            executeRequest(buildRequest(url))
        }
    }

    /**
     * 发表视频评论。
     *
     * 官网接口：POST /createComment
     * Content-Type: application/x-www-form-urlencoded
     * 需要 x-csrf-token header 和 x-requested-with: XMLHttpRequest
     *
     * Body 参数：
     * - _token: CSRF Token
     * - comment-user-id: 当前登录用户 ID
     * - comment-type: 固定 "video"
     * - comment-foreign-id: 视频 ID
     * - comment-count: 当前评论数
     * - comment-text: 评论内容
     *
     * 返回 JSON：{"comment_id": ..., "comment_count": ..., "single_video_comment": "<HTML>"}
     */
    suspend fun postComment(
        videoId: String,
        commentText: String,
        commentCount: Int,
        csrfToken: String,
        userId: String
    ): String {
        AppLogger.log("NetworkService", "postComment called, videoId: $videoId, userId: $userId")
        return withContext(Dispatchers.IO) {
            val baseUrl = getCurrentBaseUrl()
            val form = FormBody.Builder()
                .add("_token", csrfToken)
                .add("comment-user-id", userId)
                .add("comment-type", "video")
                .add("comment-foreign-id", videoId)
                .add("comment-count", commentCount.toString())
                .add("comment-text", commentText)
                .build()
            val req = Request.Builder()
                .url("$baseUrl/createComment")
                .post(form)
                .header("User-Agent", uaString)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Referer", "$baseUrl/")
                .header("Origin", baseUrl)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            AppLogger.log("NetworkService", "Posting comment to: $baseUrl/createComment")
            client.newCall(req).execute().use { response ->
                val code = response.code
                AppLogger.log("NetworkService", "postComment response code: $code")
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    throw Exception("发表评论失败 (HTTP $code)")
                }
                body ?: throw Exception("发表评论返回空响应")
            }
        }
    }
}

data class FetchResult(val html: String, val baseUrl: String)