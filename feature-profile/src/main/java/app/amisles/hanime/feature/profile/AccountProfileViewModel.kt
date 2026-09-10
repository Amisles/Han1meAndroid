package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.result.AppResult
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 账户资料页的视图状态。
 * - Idle：初始/未操作
 * - Loading：提交更新中
 * - Success：更新成功
 * - Error：更新失败（附错误信息）
 */
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
            when (val result = repository.getAccountProfile()) {
                is AppResult.Success -> {
                    val profile = result.data
                    _name.value = profile.name
                    _email.value = profile.email
                    csrfToken = profile.csrfToken
                    _isLoading.value = false
                }
                is AppResult.Error -> {
                    // 加载失败必须清空令牌：否则重试提交会带着空/过期令牌发出去，
                    // 服务端只会返回 419/403，用户看到的失败原因与真实原因无关（审查 P3）
                    csrfToken = ""
                    _error.value = result.message
                    _isLoading.value = false
                }
                else -> _isLoading.value = false
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
            when (val result = repository.updateAccountProfile(_name.value, _email.value, csrfToken)) {
                is AppResult.Success -> {
                    _updateState.value = AccountUpdateState.Success
                }
                is AppResult.Error -> {
                    _updateState.value = AccountUpdateState.Error(result.message)
                }
                else -> _updateState.value = AccountUpdateState.Idle
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = AccountUpdateState.Idle
    }
}
