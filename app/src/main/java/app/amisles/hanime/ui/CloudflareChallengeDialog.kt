package app.amisles.hanime.ui

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.data.remote.EntryInterceptor
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 轮询内置浏览器 Cookie 的间隔：太密没必要，太疏会让用户盯着「加载中」。 */
private const val COOKIE_POLL_INTERVAL_MS = 500L

private const val CLEARANCE_COOKIE = "cf_clearance="

/** Cloudflare 校验窗口允许加载的域名：本站 + 校验服务本身，其余一律交给系统浏览器。 */
private val CHALLENGE_AUXILIARY_HOSTS = listOf("cloudflare.com", "cloudflarechallenge.com")

@HiltViewModel
class CloudflareChallengeViewModel @Inject constructor(
    private val session: SessionRepository,
    private val network: NetworkService
) : ViewModel() {

    suspend fun save(host: String, cookie: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { session.saveCloudFlareCookie(cookie, host) }
            .onFailure { e ->
                AppLogger.logError("CloudflareChallenge", "保存校验 Cookie 失败: ${e.message}", e)
            }
            .isSuccess
    }

    /** 用后台同一套请求栈复查：只有不再被判为挑战页，才算真的过了。 */
    suspend fun verify(): Boolean = withContext(Dispatchers.IO) {
        runCatching { network.fetchHomePageWithBaseUrl() }.isSuccess
    }
}

/**
 * 手动完成 Cloudflare 人机校验。
 *
 * 站点把请求拦成挑战页时没有可编程的通过方式，只能让用户在真实浏览器里点一次；
 * 校验结果以 `cf_clearance` Cookie 的形式落到 WebView 的 Cookie 存储里，
 * 由 [CookieManager] 取出、交给 [SessionRepository] 持久化，之后的请求即可自动带上。
 *
 * 内置浏览器必须与后台请求使用同一个 UA（见 [BrowserUserAgent]），否则拿到的 Cookie 后端不认。
 */
@Composable
fun CloudflareChallengeDialog(
    host: String,
    onDismiss: () -> Unit,
    onSolved: () -> Unit,
    viewModel: CloudflareChallengeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entryUrl = remember(host) { "https://$host" + EntryInterceptor.entryPathFor(host) }
    // 先清掉旧的 cf_clearance：留着它，内置浏览器会直接跳过校验，下面的轮询也会立刻判成功，
    // 表现就是窗口一闪而过、提示验证成功，而保存下来的是一张早就失效的 Cookie
    val staleCookie = remember(host) {
        val manager = CookieManager.getInstance()
        val existing = manager.getCookie(entryUrl)?.let(::clearanceCookie)
        manager.setCookie(entryUrl, CLEARANCE_COOKIE + "; Max-Age=0; Path=/")
        manager.flush()
        existing
    }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(10.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.cloudflare_verify_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            createChallengeWebView(
                                context = ctx,
                                entryUrl = entryUrl,
                                challengeHost = host,
                                onFinished = { loading = false }
                            ).also { webViewRef.value = it }
                        }
                    )
                    if (loading || saving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.cloudflare_verify_loading),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.cloudflare_verify_hint),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }

    LaunchedEffect(entryUrl) {
        val cookieManager = CookieManager.getInstance()
        var lastSeen = staleCookie
        while (true) {
            delay(COOKIE_POLL_INTERVAL_MS)
            val clearance = cookieManager.getCookie(entryUrl)?.let(::clearanceCookie) ?: continue
            if (clearance == lastSeen) continue
            saving = true
            val works = viewModel.save(host, clearance) && viewModel.verify()
            saving = false
            lastSeen = clearance
            if (works) {
                Toast.makeText(
                    context,
                    context.getString(R.string.cloudflare_verify_success),
                    Toast.LENGTH_SHORT
                ).show()
                onSolved()
                return@LaunchedEffect
            }
            Toast.makeText(
                context,
                context.getString(R.string.cloudflare_verify_ineffective),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            webViewRef.value?.apply {
                stopLoading()
                onPause()
                destroy()
            }
            webViewRef.value = null
        }
    }
}

private fun createChallengeWebView(
    context: android.content.Context,
    entryUrl: String,
    challengeHost: String,
    onFinished: () -> Unit
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    // 与后台请求保持一字不差：cf_clearance 与 UA 绑定
    settings.userAgentString = BrowserUserAgent.VALUE
    // 校验页是 HTTPS，放开 http 子资源等于给中间人留注入脚本的口子
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(this, true)

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            if (isChallengeUrlAllowed(target, challengeHost)) return false
            // 该 WebView 会参与校验 Cookie 的写入，不能让任意站点在其中渲染
            AppLogger.w(
                "CloudflareChallenge",
                "拦截非白名单导航，改用系统浏览器: ${target.redactUrlForLog()}"
            )
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, request.url).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            return true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onFinished()
        }

        // 加载失败也要收起遮罩：否则用户对着转圈无从下手，也不知道该换网络
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) onFinished()
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            // 证书错误一律拒绝：继续加载会让校验页可被中间人替换
            handler?.cancel()
        }
    }

    loadUrl(entryUrl)
}

/** 只取出 `cf_clearance=` 这一段：登录态会话不该被塞进校验 Cookie 的槽位。 */
internal fun clearanceCookie(raw: String): String? =
    raw.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith(CLEARANCE_COOKIE) && it.length > CLEARANCE_COOKIE.length }

private fun isChallengeUrlAllowed(url: String, challengeHost: String): Boolean {
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    if (host == challengeHost || host.endsWith(".$challengeHost")) return true
    return CHALLENGE_AUXILIARY_HOSTS.any { host == it || host.endsWith(".$it") }
}
