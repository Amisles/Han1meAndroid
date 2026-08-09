package app.amisles.hanime.feature.profile

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

private fun isLoginPage(url: String): Boolean =
    LOGIN_URLS.any { base ->
        url.startsWith(base.substringBefore("/login")) && url.contains("/login")
    }

private fun isLoggedInRedirect(url: String): Boolean {
    val host = listOf("hanime1.me", "hanimeone.me")
    return host.any { url.contains(it) } && !isLoginPage(url) && !url.contains("/logout")
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
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

            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 2.dp
                    )
                },
                divider = { }
            ) {
                listOf("账号密码", "WebView 登录", "手动 Cookie").forEachIndexed { idx, title ->
                    Tab(
                        selected = tabIndex == idx,
                        onClick = { tabIndex = idx; vm.reset() },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            title,
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
    var password by rememberSaveable { mutableStateOf("") }
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
            "如遇到 Cloudflare 验证或登录失败，请切换到 WebView 登录。",
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
                        Text("一键切换到 WebView 登录", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    text = if (isLoading) "登录中..." else stringResource(R.string.login_button),
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
                            if (isLoggedInRedirect(url)) {
                                val cookies =
                                    cookieManager.getCookie("https://hanime1.me")
                                        ?: cookieManager.getCookie("https://hanimeone.me")
                                        ?: return false
                                if (cookies.contains("laravel_session", true) ||
                                    cookies.contains("session", true)) {
                                    vm.saveWebViewCookie(cookies)
                                }
                                return true
                            }
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            view ?: return
                            val cookies = CookieManager.getInstance().getCookie(url) ?: return
                            if (isLoggedInRedirect(url ?: "") &&
                                (cookies.contains("laravel_session", true) ||
                                    cookies.contains("session", true))) {
                                vm.saveWebViewCookie(cookies)
                            }
                        }
                    }

                    post {
                        if (configured.value) return@post
                        configured.value = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(false)
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode =
                            android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                        loadUrl(LOGIN_URLS.first())
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { wv ->
                val u = wv.url.orEmpty()
                if (u.isNotBlank() && u != LOGIN_URLS.first()) {
                    val cookies = CookieManager.getInstance().getCookie(u) ?: return@AndroidView
                    if (isLoggedInRedirect(u) &&
                        (cookies.contains("laravel_session", true) ||
                            cookies.contains("session", true))) {
                        vm.saveWebViewCookie(cookies)
                    }
                }
            }
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
                    Text("正在加载登录页...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }
}

@Composable
private fun ManualCookieTab(vm: LoginViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
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
            "手动粘贴 Cookie",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "从浏览器登录官网后，打开开发者工具复制 Cookie 粘贴到这里。\n格式：laravel_session=xxx; XSRF-TOKEN=yyy",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Cookie 字符串", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                text = if (isLoading) "保存中..." else "保存并登录",
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