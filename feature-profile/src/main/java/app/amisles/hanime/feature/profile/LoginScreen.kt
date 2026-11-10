package app.amisles.hanime.feature.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.CookieManager
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.LoginUnsupportedBanner
import androidx.compose.material3.MaterialTheme
import java.util.concurrent.atomic.AtomicLong

/**
 * 登录 WebView 的落地页：只有官方域名能登录，镜像站的登录接口会返回「站点维护中」。
 * 页内跳转、白名单判定等策略见 [WebLoginPolicy]（纯逻辑，有单测覆盖）。
 */
private val LOGIN_URL = WebLoginPolicy.LOGIN_URLS.first()

/** 两次会话校验的最小间隔：站内来回点几下不该反复打校验请求。 */
private const val VERIFY_MIN_INTERVAL_MS = 6_000L

/**
 * 登录页底部提示。
 *
 * [canReload] 用于「流程被打断」的场景（外跳被拦、Google 拒绝内置浏览器）：
 * 此时 WebView 可能停在半路的页面，给一个直接重开登录页的出口，免得用户只能退出重进。
 */
private data class LoginNotice(
    val message: String,
    val canReload: Boolean = false,
    /**
     * 授权途中的非白名单跳转：白名单不可能穷举 Google 的每一次跳转，拦死会让用户卡死，
     * 因此给一个「仍要打开」的出口。URL 由用户点选，未点选前不会加载。
     */
    val openAnywayUrl: String? = null
)

/** 一次外部跳转被拦下时的提示。带上目标 host，出问题时用户能直接说出是哪一跳。 */
private fun blockedNavigationNotice(context: Context, url: String): String {
    val target = WebLoginPolicy.hostOf(url) ?: url.redactUrlForLog()
    return context.getString(R.string.login_webview_blocked_navigation, target)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val appPrefs = LocalAppPreferences.current
    val vm: LoginViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val isLoginSupported by appPrefs.loginSupportedFlow.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is LoginViewModel.UiState.Success) {
            vm.reset()
            onLoginSuccess()
        }
    }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.login_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 当前镜像站不支持登录时，顶部显示常驻提示横幅；WebView / 手动 Cookie 仍可使用
            if (!isLoginSupported) {
                LoginUnsupportedBanner(onGoToSettings = onNavigateToSettings)
            }

            PrimaryTabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                divider = { }
            ) {
                listOf(
                    R.string.login_tab_password,
                    R.string.login_tab_webview,
                    R.string.login_tab_cookie
                ).forEachIndexed { idx, titleRes ->
                    Tab(
                        selected = tabIndex == idx,
                        onClick = { tabIndex = idx; vm.reset() },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            stringResource(titleRes),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }

            when (tabIndex) {
                0 -> EmailPasswordTab(
                    vm = vm,
                    onSwitchToWebView = { tabIndex = 1; vm.reset() }
                )
                1 -> WebViewLoginTab(vm = vm)
                2 -> ManualCookieTab(vm = vm)
            }
        }
    }
}

@Composable
private fun EmailPasswordTab(
    vm: LoginViewModel,
    onSwitchToWebView: () -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    // 密码不落 saved instance state：rememberSaveable 会把明文写进 Bundle 并可能持久化到磁盘；
    // Activity 已声明 configChanges，旋转不重建 Activity，故无需保存密码。
    var password by remember { mutableStateOf("") }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isLoading = state is LoginViewModel.UiState.Loading
    val errorMsg = (state as? LoginViewModel.UiState.Error)?.message
    val showWebViewCta = !errorMsg.isNullOrBlank() &&
            (errorMsg.contains("DNS") || errorMsg.contains("代理") ||
                    errorMsg.contains("VPN") || errorMsg.contains("Cloudflare") ||
                    errorMsg.contains("改用 WebView"))

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.login_account_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.login_cloudflare_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_email), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = outlinedDefaults(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            colors = outlinedDefaults(),
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMsg.isNullOrBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2a1414))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = errorMsg,
                    color = Color(0xFFff6b6b),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (showWebViewCta) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onSwitchToWebView,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFff6b6b)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFff6b6b),
                            disabledContentColor = Color(0xFFff6b6b).copy(alpha = .5f)
                        )
                    ) {
                        Text(
                            stringResource(R.string.login_switch_to_webview),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { vm.loginWithEmailPassword(email, password) },
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .5f),
                    disabledContentColor = Color.White.copy(alpha = .6f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(
                        if (isLoading) R.string.login_logging_in else R.string.login_button
                    ),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun WebViewLoginTab(vm: LoginViewModel) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<LoginNotice?>(null) }
    val configured = remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // 进入本 Tab 时已存在的 Cookie：用来判断当前会话是不是这一轮登录换出来的
    val cookieAtStart = remember {
        CookieManager.getInstance().getCookie(LOGIN_URL).orEmpty()
    }
    // 是否已经进入第三方授权（站内 /auth/* 或 Google 域名）。授权进行中一律不把导航抛给系统：
    // 中途页面都绑定了本次会话，离开 WebView 只会换回一个错误页，登录也再回不来。
    val oauthInProgress = remember { mutableStateOf(false) }
    // 是否走过第三方登录回调（/auth/{provider}/callback）。只有这种情况下的校验失败才提示用户：
    // 普通浏览同样会让站点轮换游客会话，把那些也算成「登录失败」只会变成噪音。
    val oauthRoundTrip = remember { mutableStateOf(false) }
    // 校验单飞 + 节流：上一次校验没回来之前不再发起第二次
    val verifying = remember { mutableStateOf(false) }
    val lastVerifyAt = remember { AtomicLong(0L) }
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginViewModel.UiState.Verifying -> {
                verifying.value = true
                notice = null
            }
            is LoginViewModel.UiState.Error -> {
                verifying.value = false
                notice = if (oauthRoundTrip.value) LoginNotice(state.message, canReload = true) else null
            }
            else -> Unit
        }
    }

    // 页面交还给官方域名、且会话确实换过时，才向站点确认一次身份。
    // 刻意不做「Cookie 里有 session 就判定登录成功」：游客同样拿得到 laravel_session，
    // 那样会在刚点下 Google 登录时误判成功并退出登录页，存下来的却是游客会话。
    val verifySessionIfReady: (String) -> Unit = { url ->
        val ready = !verifying.value &&
                WebLoginPolicy.isOfficialHost(url) &&
                !WebLoginPolicy.isLoginPage(url) &&
                !WebLoginPolicy.isLogoutUrl(url) &&
                // 登录入口与回调页都只是跳板，会话要到落地页才建立：
                // 在这两处校验只会得到「仍是游客」的假失败
                !WebLoginPolicy.isOAuthEntry(url)
        if (ready) {
            val cookies = CookieManager.getInstance().getCookie(url)
            val sessionReplaced = oauthRoundTrip.value || cookies != cookieAtStart
            val now = SystemClock.elapsedRealtime()
            if (WebLoginPolicy.hasSessionCookie(cookies) &&
                sessionReplaced &&
                now - lastVerifyAt.get() >= VERIFY_MIN_INTERVAL_MS
            ) {
                lastVerifyAt.set(now)
                verifying.value = true
                AppLogger.d(
                    "LoginScreen",
                    "会话已变化，向站点校验登录状态: host=${WebLoginPolicy.hostOf(url)}"
                )
                vm.completeWebViewLogin(cookies.orEmpty(), WebLoginPolicy.hostOf(url))
            }
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
                    // Google 登录是跨站授权：回调期间 Cookie 会在 hanime1.me 与 accounts.google.com
                    // 之间来回，拒绝第三方 Cookie 会让授权结果带不回来
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            // 先判 scheme：intent://hanime1.me/... 这类地址的 host 仍是官方域名，
                            // 若先过白名单就会放行，WebView 随后把它交给系统，照样把用户踢出 App
                            val scheme = WebLoginPolicy.schemeOf(url)
                            if (scheme != "http" && scheme != "https") {
                                // intent:// / market:// / mailto: 这类跳转不能放行：WebView 会把它们直接
                                // 交给系统（Google 授权收尾时就用 intent:// 指向 Chrome），用户被踢出 App，
                                // 而会话只在本 WebView 里，回来时只剩一个绑定会话的报错页。
                                AppLogger.w("LoginScreen", "拦截非 http(s) 跳转: ${url.redactUrlForLog()}")
                                notice = if (WebLoginPolicy.isOAuthHost(url)) {
                                    LoginNotice(ctx.getString(R.string.login_webview_oauth_blocked), canReload = true)
                                } else {
                                    LoginNotice(blockedNavigationNotice(ctx, url), canReload = true)
                                }
                                return true
                            }

                            // 官方域名与 Google 授权链路必须留在本 WebView 内：会话只会写回这里的
                            // CookieManager，跳到系统浏览器即便用户登录成功，App 也拿不到那份 Cookie
                            if (WebLoginPolicy.isAllowedInWebView(url)) return false

                            // 授权进行中：中途的 Google 页面与本次会话绑定，外跳只会得到 400
                            if (oauthInProgress.value) {
                                AppLogger.w("LoginScreen", "授权进行中，拦截外跳: ${url.redactUrlForLog()}")
                                notice = LoginNotice(
                                    message = blockedNavigationNotice(ctx, url),
                                    canReload = true,
                                    openAnywayUrl = url
                                )
                                return true
                            }

                            // 其余域名不在本 WebView 内加载：它持有登录 Cookie 且开着 JS 与第三方
                            // Cookie，让任意站点在其中渲染会放大钓鱼与凭据泄露面。改用系统浏览器，不阻断用户。
                            AppLogger.w(
                                "LoginScreen",
                                "拦截非白名单导航，改用系统浏览器: ${url.redactUrlForLog()}"
                            )
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, request.url).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }.onFailure { e ->
                                AppLogger.w("LoginScreen", "打开系统浏览器失败: ${e.message}")
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            val currentUrl = url ?: return
                            if (WebLoginPolicy.isOAuthEntry(currentUrl) || WebLoginPolicy.isOAuthHost(currentUrl)) {
                                oauthInProgress.value = true
                            }
                            if (WebLoginPolicy.isOAuthCallback(currentUrl)) oauthRoundTrip.value = true
                            // Google 拒绝在内置浏览器里完成授权（disallowed_useragent）时会跳到这里，
                            // 继续等下去不会有结果，直接告诉用户换登录方式
                            if (WebLoginPolicy.isOAuthRejectedPage(currentUrl)) {
                                notice = LoginNotice(
                                    ctx.getString(R.string.login_webview_oauth_blocked),
                                    canReload = true
                                )
                            }
                        }

                        /**
                         * 服务端 302 不一定触发 onPageStarted，这里再记一次：
                         * 「走过回调」是判断这一轮登录是否真的完成的最可靠信号。
                         */
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            val currentUrl = url ?: return
                            if (WebLoginPolicy.isOAuthHost(currentUrl)) oauthInProgress.value = true
                            if (!oauthRoundTrip.value && WebLoginPolicy.isOAuthCallback(currentUrl)) {
                                oauthRoundTrip.value = true
                                AppLogger.d("LoginScreen", "检测到第三方登录回调，等待落地页后校验会话")
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            verifySessionIfReady(url ?: return)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                loading = false
                                loadError = true
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // 任何证书错误都直接拒绝：显式 cancel，避免默认实现模糊或将来被误改为 proceed
                            handler?.cancel()
                        }
                    }

                    post {
                        if (configured.value) return@post
                        configured.value = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(false)
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // 禁止混合内容：登录页为 HTTPS，若允许加载 http 子资源（尤其脚本），
                        // 中间人可注入脚本并以登录页源读取表单内容。若线上确因 http 子资源
                        // 导致渲染异常，再评估放宽为 MIXED_CONTENT_COMPATIBILITY_MODE。
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        // Google 的账号选择 / 授权确认可能走 window.open：保持「在同一个 WebView 内打开」，
                        // 并允许脚本发起新窗口，否则授权会停在空白页
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        // 与后台请求同一串 UA：cf_clearance 与 UA 绑定，不一致则校验结果不生效。
                        // 这串 UA 也不带 WebView 的 "; wv" 标记 —— Google 只凭 UA 就能把内置浏览器
                        // 认出来并以 disallowed_useragent 拒绝授权。
                        settings.userAgentString = BrowserUserAgent.VALUE
                        loadUrl(LOGIN_URL)
                    }
                }.also { webViewRef.value = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
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
        } else if (loadError) {
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
                        loadError = false
                        loading = true
                        webViewRef.value?.reload()
                    }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }

        // 校验本身是一次网络请求，页面上看不出任何变化，必须有明确进度，否则用户会以为点坏了
        if (verifying.value) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f)),
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

        // 校验未通过 / 外跳被拦 / Google 拒绝内置浏览器：停留在登录页说明下一步，而不是默默退回抽屉
        notice?.let { current ->
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
                    // 让用户能越过白名单：Google 的跳转链是黑盒，白名单漏一个就卡死整个登录
                    if (current.openAnywayUrl != null) {
                        TextButton(onClick = {
                            val target = current.openAnywayUrl
                            notice = null
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
                            // 重开登录页时把「已进入授权」「走过回调」一起复位：
                            // 否则新的一轮会被上一轮的标记影响，正常浏览也会被当成授权中途而拦下
                            notice = null
                            oauthInProgress.value = false
                            oauthRoundTrip.value = false
                            loadError = false
                            loading = true
                            webViewRef.value?.loadUrl(LOGIN_URL)
                        }) {
                            Text(
                                text = stringResource(R.string.login_webview_reload_login),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    TextButton(onClick = { notice = null }) {
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

@Composable
private fun ManualCookieTab(vm: LoginViewModel) {
    // 手动 Cookie 同样是凭据，与密码一致，不写进 saved instance state
    var text by remember { mutableStateOf("") }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isLoading = state is LoginViewModel.UiState.Loading
    val errorMsg = (state as? LoginViewModel.UiState.Error)?.message

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.login_manual_cookie_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.login_manual_cookie_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.login_cookie_label), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !isLoading,
            minLines = 6,
            maxLines = 12,
            colors = outlinedDefaults(),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        if (!errorMsg.isNullOrBlank()) {
            Text(
                text = errorMsg,
                color = Color(0xFFff6b6b),
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = { vm.saveManualCookie(text) },
            enabled = !isLoading && text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .5f),
                disabledContentColor = Color.White.copy(alpha = .6f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    if (isLoading) R.string.login_saving else R.string.login_save_and_login
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun outlinedDefaults() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = Color(0xFFff6b6b),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorLabelColor = Color(0xFFff6b6b)
)