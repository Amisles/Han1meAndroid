package app.amisles.hanime.feature.profile

import app.amisles.hanime.core.ui.util.AppSnackbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.LoginUnsupportedBanner
import androidx.compose.material3.MaterialTheme

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

    val context = LocalContext.current
    LaunchedEffect(uiState) {
        val state = uiState as? LoginViewModel.UiState.Success ?: return@LaunchedEffect
        if (state.memoryOnly) {
            AppSnackbar.show(context.getString(R.string.login_memory_only))
        }
        vm.reset()
        onLoginSuccess()
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