package app.amisles.hanime.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.AuthorPageData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthorViewModel @Inject constructor(
    private val networkService: NetworkService
) : ViewModel() {

    private val _authorData = MutableStateFlow<AuthorPageData?>(null)
    val authorData: StateFlow<AuthorPageData?> = _authorData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadAuthorPage(authorPageUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = networkService.fetchAuthorPage(authorPageUrl)
                if (result != null) {
                    _authorData.value = result
                } else {
                    _error.value = "无法解析作者主页"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
