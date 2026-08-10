package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: HanimeRepository
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val cookieLength: Int) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loginWithEmailPassword(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = UiState.Error("请输入邮箱和密码")
            return
        }
        if (!cleanEmail.contains('@')) {
            _uiState.value = UiState.Error("请输入正确的邮箱地址")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.login(cleanEmail, password)
            when (result) {
                is AppResult.Success -> _uiState.value = UiState.Success(result.data.length)
                is AppResult.Error -> _uiState.value = UiState.Error(result.message)
                is AppResult.Loading -> {}
            }
        }
    }

    fun saveManualCookie(cookieString: String) {
        val clean = cookieString.trim()
        if (clean.isEmpty()) {
            _uiState.value = UiState.Error("Cookie 不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val ok = repository.saveLoginCookie(clean)
            if (ok) {
                _uiState.value = UiState.Success(clean.length)
            } else {
                _uiState.value = UiState.Error("保存失败，请检查 Cookie 格式")
            }
        }
    }

    fun saveWebViewCookie(cookieFromManager: String) {
        saveManualCookie(cookieFromManager)
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun logout() {
        repository.logout()
    }
}
