package app.amisles.hanime.feature.detail

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DetailSubscriptionActions(
    private val account: AccountRepository,
    private val scope: CoroutineScope
) {
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _isSubscribing = MutableStateFlow(false)
    val isSubscribing: StateFlow<Boolean> = _isSubscribing.asStateFlow()

    private val _error = MutableStateFlow<SubscribeError?>(null)
    val error: StateFlow<SubscribeError?> = _error.asStateFlow()

    fun reset(detail: VideoDetail?) {
        _isSubscribed.value = detail?.subscribeStatus == "1"
        _isSubscribing.value = false
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun toggle(
        detail: VideoDetail?,
        requestVideoId: String,
        isCurrent: () -> Boolean,
        onCsrfToken: (String) -> Unit
    ) {
        if (detail == null || detail.subscribeArtistId.isBlank()) {
            _error.value = SubscribeError.ARTIST_ID_MISSING
            return
        }
        if (detail.csrfToken.isBlank()) {
            _error.value = SubscribeError.CSRF_MISSING
            return
        }
        if (detail.subscribeUserId.isBlank()) {
            _error.value = SubscribeError.NOT_LOGGED_IN
            return
        }
        if (_isSubscribing.value) return

        val willSubscribe = !_isSubscribed.value
        _isSubscribed.value = willSubscribe
        _isSubscribing.value = true

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.toggleSubscribe(
                        userId = detail.subscribeUserId,
                        artistId = detail.subscribeArtistId,
                        willSubscribe = willSubscribe,
                        csrfToken = detail.csrfToken
                    )
                }
                if (!isCurrent()) {
                    AppLogger.d(
                        "DetailSubscriptionActions",
                        "Discard stale subscribe result: ${detail.subscribeArtistId}"
                    )
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        val response = result.data
                        _isSubscribed.value = response.subscribeStatus == "1"
                        if (response.csrfToken.isNotBlank()) onCsrfToken(response.csrfToken)
                    }
                    is AppResult.Error -> {
                        _isSubscribed.value = !willSubscribe
                        _error.value = SubscribeError.FAILED
                        AppLogger.e(
                            "DetailSubscriptionActions",
                            "Error toggling subscribe: ${result.message}",
                            result.exception
                        )
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DetailSubscriptionActions", "Error toggling subscribe: ${e.message}", e)
                if (isCurrent()) {
                    _isSubscribed.value = !willSubscribe
                    _error.value = SubscribeError.FAILED
                }
            } finally {
                // 只在仍是本次请求的视频时复位进行中标记，避免覆盖切视频后新一轮订阅的状态
                if (isCurrent()) _isSubscribing.value = false
            }
        }
    }
}
