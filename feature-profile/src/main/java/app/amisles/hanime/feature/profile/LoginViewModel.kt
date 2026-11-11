package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.domain.result.WebLoginVerification
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AccountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState

        /** WebView 里已经拿到会话，正在向站点确认这份会话是否真的登录成功。 */
        data object Verifying : UiState
        data class Success(val cookieLength: Int) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 正在进行的会话校验：同一时刻只允许一次。 */
    private var verifyJob: Job? = null

    fun loginWithEmailPassword(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = UiState.Error(context.getString(R.string.login_enter_email_password))
            return
        }
        if (!cleanEmail.contains('@')) {
            _uiState.value = UiState.Error(context.getString(R.string.login_invalid_email))
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val result = repository.login(cleanEmail, password)
                when (result) {
                    is AppResult.Success -> _uiState.value = UiState.Success(result.data.length)
                    is AppResult.Error -> _uiState.value = UiState.Error(
                        ErrorMapper.message(
                            context,
                            result.exception,
                            // 仓储把域名/凭据类失败包装成 IllegalStateException，文案已面向用户，
                            // 这里落到通用兜底会把它吞成「加载失败」，用户看不到该改用 WebView 的提示
                            result.message.ifBlank { context.getString(R.string.common_load_failed) }
                        )
                    )
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 否则 _uiState 会永久停留在 Loading，登录按钮一直转圈
                AppLogger.e("LoginViewModel", "登录失败: ${e.message}", e)
                _uiState.value = UiState.Error(
                    ErrorMapper.message(
                        context,
                        e,
                        context.getString(R.string.common_load_failed)
                    )
                )
            }
        }
    }

    fun saveManualCookie(cookieString: String) {
        val clean = cookieString.trim()
        if (clean.isEmpty()) {
            _uiState.value = UiState.Error(context.getString(R.string.login_cookie_empty))
            return
        }
        if (!containsSessionKey(clean)) {
            _uiState.value = UiState.Error(context.getString(R.string.login_cookie_invalid))
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val ok = repository.saveLoginCookie(clean)
                if (ok) {
                    _uiState.value = UiState.Success(clean.length)
                } else {
                    _uiState.value = UiState.Error(context.getString(R.string.login_cookie_invalid))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preferences 未初始化（lateinit）等异常会让 _uiState 永久停留在 Loading
                AppLogger.e("LoginViewModel", "保存 Cookie 失败: ${e.message}", e)
                _uiState.value = UiState.Error(context.getString(R.string.login_cookie_invalid))
            }
        }
    }

    private fun containsSessionKey(cookie: String): Boolean =
        cookie.contains("laravel_session", ignoreCase = true) ||
            cookie.contains("session", ignoreCase = true)

    /**
     * WebView 拿到会话后的收尾：先向站点确认身份，再按校验结果的三态决定是否落盘。
     *
     * 站点对游客同样下发 laravel_session，只凭 Cookie 内容判定「已登录」会把游客会话存成
     * 登录态 —— 界面显示已登录、抽屉显示错误的用户名，随后任何需要身份的功能都失败。
     * 因此判定必须回落到服务端页面（见 [AccountRepository.verifyWebLogin]）。
     *
     * 校验结果分三态：Verified 落盘并带上 userId；NotLoggedIn 明确是游客会话，报错且不落盘；
     * Inconclusive 表示断网或人机校验挡住了探针、真假难辨，仍按最佳努力落盘（userId 为 null），
     * 交由后续请求暴露问题。
     *
     * [sourceDomain] 是实际完成登录的站点 host：会话只能注入回该域名，避免用户之后把
     * baseUrl 改成镜像站时把主站会话一并发送过去。
     */
    fun completeWebViewLogin(cookieFromManager: String, sourceDomain: String?) {
        val clean = cookieFromManager.trim()
        if (clean.isEmpty() || sourceDomain.isNullOrBlank()) {
            _uiState.value = UiState.Error(context.getString(R.string.login_cookie_empty))
            return
        }
        // 单飞：WebView 的 onPageFinished 会连续触发，重复发起只会互相覆盖结果。
        // 用 Job 而不是状态位判重 —— 正在跑的那次总会以 Success/Error 收尾，界面上的「校验中」
        // 因此一定会被清掉；用状态位判重则可能出现「没人再改状态、转圈不停」的死角。
        if (verifyJob?.isActive == true) return
        verifyJob = viewModelScope.launch {
            _uiState.value = UiState.Verifying
            try {
                when (val verification = repository.verifyWebLogin(clean, sourceDomain)) {
                    is WebLoginVerification.Verified ->
                        finishWebLogin(clean, sourceDomain, verification.userId)

                    WebLoginVerification.NotLoggedIn ->
                        _uiState.value =
                            UiState.Error(context.getString(R.string.login_webview_not_completed))

                    // 断网、人机校验挡住了这次探针：真假难辨，先保存，交给后续请求暴露问题
                    WebLoginVerification.Inconclusive ->
                        finishWebLogin(clean, sourceDomain, null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("LoginViewModel", "WebView 登录校验失败: ${e.message}", e)
                _uiState.value = UiState.Error(
                    ErrorMapper.message(context, e, context.getString(R.string.common_load_failed))
                )
            }
        }
    }

    private suspend fun finishWebLogin(cookie: String, domain: String, userId: String?) {
        val ok = repository.saveLoginCookie(cookie, domain, userId)
        _uiState.value = if (ok) {
            UiState.Success(cookie.length)
        } else {
            UiState.Error(context.getString(R.string.login_cookie_invalid))
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun logout() {
        repository.logout()
    }
}
