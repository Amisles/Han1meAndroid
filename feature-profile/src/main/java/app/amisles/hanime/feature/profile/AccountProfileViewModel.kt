package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.result.AppResult
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账户资料页的视图状态：Idle 初始 / Loading 提交中 / Success 成功 / Error 失败（附信息）。 */
sealed interface AccountUpdateState {
    data object Idle : AccountUpdateState
    data object Loading : AccountUpdateState
    data object Success : AccountUpdateState
    data class Error(val message: String) : AccountUpdateState
}

@HiltViewModel
class AccountProfileViewModel @Inject constructor(
    private val repository: HanimeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _updateState = MutableStateFlow<AccountUpdateState>(AccountUpdateState.Idle)
    val updateState: StateFlow<AccountUpdateState> = _updateState.asStateFlow()

    private var csrfToken: String = ""

    fun load() {
        if (Preferences.savedUserId.isBlank()) {
            // 未登录态下不能留有上一次的 CSRF 令牌，否则 update() 会发一个注定失败的请求
            csrfToken = ""
            _error.value = context.getString(R.string.account_error_not_logged_in)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = repository.getAccountProfile()) {
                    is AppResult.Success -> {
                        val profile = result.data
                        _name.value = profile.name
                        _email.value = profile.email
                        csrfToken = profile.csrfToken
                    }
                    is AppResult.Error -> {
                        // 加载失败必须清空令牌：否则重试会带着空/过期令牌提交，服务端返回 419/403
                        csrfToken = ""
                        _error.value = result.message
                    }
                    else -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 解析等运行期异常若逃逸出 viewModelScope 会直接崩溃，且会跳过加载态复位
                AppLogger.e("AccountProfileViewModel", "加载账户资料失败: ${e.message}", e)
                csrfToken = ""
                _error.value = e.message ?: context.getString(R.string.common_load_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onEmailChange(value: String) {
        _email.value = value
    }

    fun update() {
        if (_name.value.isBlank()) {
            _updateState.value = AccountUpdateState.Error(context.getString(R.string.account_error_name_empty))
            return
        }
        if (csrfToken.isBlank()) {
            // 令牌缺失说明资料还没成功加载过：先给出可理解的提示，不发注定失败的表单
            _updateState.value = AccountUpdateState.Error(context.getString(R.string.error_csrf_missing))
            return
        }
        viewModelScope.launch {
            _updateState.value = AccountUpdateState.Loading
            try {
                when (val result = repository.updateAccountProfile(_name.value, _email.value, csrfToken)) {
                    is AppResult.Success -> {
                        _updateState.value = AccountUpdateState.Success
                    }
                    is AppResult.Error -> {
                        _updateState.value = AccountUpdateState.Error(result.message)
                    }
                    else -> _updateState.value = AccountUpdateState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 否则 _updateState 会永久停留在 Loading，提交按钮一直转圈
                AppLogger.e("AccountProfileViewModel", "更新账户资料失败: ${e.message}", e)
                _updateState.value = AccountUpdateState.Error(
                    e.message ?: context.getString(R.string.common_load_failed)
                )
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = AccountUpdateState.Idle
    }
}
