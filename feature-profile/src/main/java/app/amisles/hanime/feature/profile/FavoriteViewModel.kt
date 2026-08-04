package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repository: HanimeRepository
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<FavoriteVideo>>(emptyList())
    val favorites: StateFlow<List<FavoriteVideo>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadFavorites() {
        AppLogger.d("FavoriteViewModel", "loadFavorites called")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val favorites = withContext(Dispatchers.IO) {
                    repository.getAllFavorites()
                }
                _favorites.value = favorites
            } catch (e: Exception) {
                AppLogger.e("FavoriteViewModel", "Error loading favorites: ${e.message}", e)
                _favorites.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFavorite(videoId: String) {
        AppLogger.d("FavoriteViewModel", "removeFavorite called, videoId: $videoId")

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.removeFavorite(videoId)
                }
                loadFavorites()
            } catch (e: Exception) {
                AppLogger.e("FavoriteViewModel", "Error removing favorites: ${e.message}", e)
            }
        }
    }

    fun removeFavorites(videoIds: List<String>) {
        AppLogger.d("FavoriteViewModel", "removeFavorites called, count: ${videoIds.size}")

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    videoIds.forEach { videoId ->
                        repository.removeFavorite(videoId)
                    }
                }
                loadFavorites()
            } catch (e: Exception) {
                AppLogger.e("FavoriteViewModel", "Error removing favorites: ${e.message}", e)
            }
        }
    }
}
