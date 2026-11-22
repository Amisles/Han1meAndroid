package app.amisles.hanime.feature.profile

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.core.ui.R

/** 登录 WebView 的落地页：只有官方域名能登录，镜像站登录接口会返回「站点维护中」。 */
private val LOGIN_URL = WebLoginPolicy.LOGIN_URLS.first()

@Composable
internal fun WebViewLoginTab(vm: LoginViewModel) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val configured = remember { mutableStateOf(false) }
    // 进入本 Tab 时已存在的 Cookie：用来判断当前会话是不是这一轮登录换出来的。
    val session = remember(vm) {
        LoginWebSession(
            cookieAtStart = CookieManager.getInstance().getCookie(LOGIN_URL).orEmpty(),
            onLoginVerified = vm::completeWebViewLogin
        )
    }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    // 遮罩由状态派生：手写开关在 ViewModel 单飞忽略调用时会永远卡在 true。
    val verifying = uiState is LoginViewModel.UiState.Verifying

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginViewModel.UiState.Verifying -> session.notice = null
            is LoginViewModel.UiState.Error -> session.showError(state.message)
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    // Google 授权是跨站流程：回调期间 Cookie 在 hanime1.me 与 accounts.google.com 之间往返
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    webViewClient = LoginWebViewClient(ctx, session)

                    post {
                        if (configured.value) return@post
                        configured.value = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(false)
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // 登录页为 HTTPS，允许 http 子资源会让中间人注入脚本读取表单
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        // Google 账号选择可能走 window.open：留在同一 WebView 内并允许脚本开窗
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        // 与后台请求同一串 UA：cf_clearance 与 UA 绑定，且不能带 WebView 的 "; wv" 标记
                        settings.userAgentString = BrowserUserAgent.VALUE
                        loadUrl(LOGIN_URL)
                    }
                }.also { webViewRef.value = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (session.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.login_page_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (session.loadError) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.common_load_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        session.loadError = false
                        session.loading = true
                        webViewRef.value?.reload()
                    }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }

        // 校验本身是一次网络请求，页面上看不出变化，必须有明确进度。
        if (verifying) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.login_webview_verifying),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        session.notice?.let { current ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current.openAnywayUrl != null) {
                        TextButton(onClick = {
                            val target = current.openAnywayUrl
                            session.notice = null
                            webViewRef.value?.loadUrl(target)
                        }) {
                            Text(
                                text = stringResource(R.string.login_webview_open_anyway),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (current.canReload) {
                        TextButton(onClick = {
                            // 新的一轮登录必须复位授权标记，否则正常浏览会被上一轮当成授权中途而拦下。
                            session.reloadLogin()
                            webViewRef.value?.loadUrl(LOGIN_URL)
                        }) {
                            Text(
                                text = stringResource(R.string.login_webview_reload_login),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    TextButton(onClick = { session.notice = null }) {
                        Text(
                            text = stringResource(R.string.login_webview_notice_dismiss),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webViewRef.value?.apply {
                stopLoading()
                onPause()
                destroy()
            }
        }
    }
}
