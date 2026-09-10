package app.amisles.hanime.feature.profile

import android.net.Uri
import android.webkit.CookieManager
import android.net.http.SslError
import android.webkit.SslErrorHandler
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
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.LoginUnsupportedBanner
import app.amisles.hanime.data.preferences.Preferences
import androidx.compose.material3.MaterialTheme

private val LOGIN_URLS = listOf("https://hanime1.me/login", "https://hanimeone.me/login")

/** 官方域名（含子域）：登录流程只允许发生在这两个域下。 */
private val OFFICIAL_HOSTS = listOf("hanime1.me", "hanimeone.me")

/**
 * 是否为官方域名（含子域）。
 * 用解析出的 host 精确比较，避免旧实现 `url.contains("hanime1.me")` 在查询参数等位置被伪造命中。
 */
private fun isOfficialHost(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
    return OFFICIAL_HOSTS.any { host == it || host.endsWith(".$it") }
}

private fun isLoginPage(url: String): Boolean =
    isOfficialHost(url) && Uri.parse(url).path.orEmpty().contains("/login")

private fun hasSessionCookie(cookie: String?): Boolean =
    cookie != null &&
        (cookie.contains("laravel_session", ignoreCase = true) ||
            cookie.contains("session", ignoreCase = true))

/**
 * 判断一次站内导航是否代表「本次登录成功」。
 *
 * 旧实现把官方域下「除登录页/登出页以外的任意页面」都视为登录成功，于是用户在登录页点
 * 忘记密码 / 注册等同域链接就会被当作登录完成并带离登录页（审查 P4）。
 * 现在要求同时满足：
 * 1. 官方域名，且不是登录页 / 登出页；
 * 2. 已经拿到会话 Cookie；
 * 3. 本次会话开始前并不持有同一个会话 Cookie（否则说明用户本来就登录着，不算本次登录成功）。
 */
private fun isLoginCompleted(url: String, currentCookie: String?, cookieAtStart: String): Boolean {
    if (!isOfficialHost(url)) return false
    if (isLoginPage(url) || url.contains("/logout")) return false
    if (!hasSessionCookie(currentCookie)) return false
    return !(currentCookie == cookieAtStart && hasSessionCookie(cookieAtStart))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val vm: LoginViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val isLoginSupported by Preferences.loginSupportedFlow.collectAsStateWithLifecycle()

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
            // 当前镜像站不支持登录时，顶部显示常驻提示横幅
            // WebView/手动 Cookie 仍可使用（WebView 使用官方域名加载登录页）
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
    // 密码不落 saved instance state：rememberSaveable 会把值写进 Bundle，系统在进程回收时
    // 会把它持久化到磁盘，等于把明文密码写盘（审查 P1）。Activity 已声明
    // orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden 的 configChanges，
    // 旋转不会重建 Activity，因此无需保存密码。
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
    val configured = remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // 进入本 Tab 时已存在的 Cookie：用于区分「本次真的登录成功」与「本来就已登录」
    val cookieAtStart = remember {
        CookieManager.getInstance().getCookie(LOGIN_URLS.first()).orEmpty()
    }
    // 登录结果只上报一次，避免同一会话内重复触发 onLoginSuccess
    val loginReported = remember { mutableStateOf(false) }
    val reportLoginIfCompleted: (String, String?) -> Unit = { url, cookies ->
        if (!loginReported.value && isLoginCompleted(url, cookies, cookieAtStart)) {
            loginReported.value = true
            cookies?.let { vm.saveWebViewCookie(it) }
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
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            reportLoginIfCompleted(url, cookieManager.getCookie(url))
                            // 不再 return true 拦截：旧实现把官方域下任意非登录页都当成登录完成，
                            // 会把忘记密码 / 注册等同域跳转挡在登录页上（审查 P4）。
                            // 放行导航本身没有副作用：登录成功时外层会立刻导航走并销毁本 WebView。
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            val currentUrl = url ?: return
                            reportLoginIfCompleted(
                                currentUrl,
                                CookieManager.getInstance().getCookie(currentUrl)
                            )
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // 任何证书错误都直接拒绝：显式 cancel 好过依赖默认实现“既不 proceed 也不 cancel”
                            // 的模糊语义，也避免将来被误改为 proceed（审查 P2 加固项）
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
                        // 保留 MIXED_CONTENT_COMPATIBILITY_MODE：登录页需要加载 http 子资源才能完整渲染，
                        // 改成 NEVER_ALLOW 会让登录页缺图/缺脚本（属历史既有的有意取舍）。
                        // 残余风险：网络中间人可替换其中的 http 子资源向登录页注入脚本。
                        // 已做的收敛：本 WebView 只用于官方域名登录页、已关闭文件/内容访问（见上）、
                        // 证书错误一律拒绝、且不再把官方域下任意跳转当作登录成功（审查 P4）。
                        // 若安全责任人确认登录页不再依赖 http 子资源，可改为 MIXED_CONTENT_NEVER_ALLOW。
                        settings.mixedContentMode =
                            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                        loadUrl(LOGIN_URLS.first())
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
    // 手动 Cookie 同样是凭据：与密码一致，不写进 saved instance state（审查 P1 同类项）。
    // 该输入框本身不影响旋转（configChanges 已覆盖），切换 Tab 时文本本就会重置。
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