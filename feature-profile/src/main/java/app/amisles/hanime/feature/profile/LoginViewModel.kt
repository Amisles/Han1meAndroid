package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.result.AppResult
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: HanimeRepository,
    @ApplicationContext private val context: Context
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
            _uiState.value = UiState.Error(context.getString(R.string.login_enter_email_password))
            return
        }
        if (!cleanEmail.contains('@')) {
            _uiState.value = UiState.Error(context.getString(R.string.login_invalid_email))
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
            _uiState.value = UiState.Error(context.getString(R.string.login_cookie_empty))
            return
        }
        if (!containsSessionKey(clean)) {
            _uiState.value = UiState.Error(context.getString(R.string.login_cookie_invalid))
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val ok = repository.saveLoginCookie(clean)
            if (ok) {
                _uiState.value = UiState.Success(clean.length)
            } else {
                _uiState.value = UiState.Error(context.getString(R.string.login_cookie_invalid))
            }
        }
    }

    private fun containsSessionKey(cookie: String): Boolean =
        cookie.contains("laravel_session", ignoreCase = true) ||
            cookie.contains("session", ignoreCase = true)

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
